package ru.alepar.zx80.machine.tape

/** Thrown when a .tzx file is structurally invalid (bad header, truncated block, etc.). */
class TzxParseException(message: String) : Exception(message)

/**
 * Parses a raw `.tzx` byte array into a [TzxTapeFile].
 *
 * Validates the 10-byte "ZXTape!" header (7 magic bytes + 0x1A EOF marker + major version + minor
 * version). Supports TZX major version 1. Then iterates block-by-block per the TZX spec.
 *
 * Recognised block IDs: 0x10, 0x11, 0x20, 0x21, 0x22, 0x30, 0x32. All other block IDs are wrapped
 * in [TzxUnknown] using each block type's length field.
 */
object TzxParser {
    private val MAGIC = byteArrayOf(0x5A, 0x58, 0x54, 0x61, 0x70, 0x65, 0x21) // "ZXTape!"
    private const val EOF_MARKER = 0x1A
    private const val SUPPORTED_MAJOR = 1

    fun parseTzx(bytes: ByteArray): TzxTapeFile {
        if (bytes.size < 10) {
            throw TzxParseException("file too short for TZX header (${bytes.size} bytes)")
        }
        for (i in MAGIC.indices) {
            if (bytes[i] != MAGIC[i]) {
                throw TzxParseException("invalid TZX magic at byte $i")
            }
        }
        if ((bytes[7].toInt() and 0xFF) != EOF_MARKER) {
            throw TzxParseException("missing TZX EOF marker (0x1A) at offset 7")
        }
        val major = bytes[8].toInt() and 0xFF
        if (major != SUPPORTED_MAJOR) {
            throw TzxParseException(
                "unsupported TZX major version $major (expected $SUPPORTED_MAJOR)"
            )
        }
        // minor version (bytes[9]) is ignored per spec; any minor is acceptable.

        val blocks = mutableListOf<TzxBlock>()
        var pos = 10
        while (pos < bytes.size) {
            val id = bytes[pos].toInt() and 0xFF
            pos++
            when (id) {
                0x10 -> {
                    // Standard Speed Data: 2-byte pause, 2-byte data length, data
                    requireBytes(bytes, pos, 4, id)
                    val pauseMs = le16(bytes, pos)
                    val dataLen = le16(bytes, pos + 2)
                    pos += 4
                    requireBytes(bytes, pos, dataLen, id)
                    val data = bytes.copyOfRange(pos, pos + dataLen)
                    pos += dataLen
                    blocks.add(TzxStandardData(pauseMs, data))
                }
                0x11 -> {
                    // Turbo Speed Data: 18-byte header + data
                    requireBytes(bytes, pos, 18, id)
                    val pilotPulse = le16(bytes, pos)
                    val sync1Pulse = le16(bytes, pos + 2)
                    val sync2Pulse = le16(bytes, pos + 4)
                    val zeroBitPulse = le16(bytes, pos + 6)
                    val oneBitPulse = le16(bytes, pos + 8)
                    val pilotToneLen = le16(bytes, pos + 10)
                    val lastByteBits = bytes[pos + 12].toInt() and 0xFF
                    val pauseMs = le16(bytes, pos + 13)
                    val dataLen = le24(bytes, pos + 15)
                    pos += 18
                    requireBytes(bytes, pos, dataLen, id)
                    val data = bytes.copyOfRange(pos, pos + dataLen)
                    pos += dataLen
                    blocks.add(
                        TzxTurboData(
                            pilotPulse,
                            sync1Pulse,
                            sync2Pulse,
                            zeroBitPulse,
                            oneBitPulse,
                            pilotToneLen,
                            lastByteBits,
                            pauseMs,
                            data,
                        )
                    )
                }
                0x20 -> {
                    // Pause / Stop the Tape: 2-byte pause in ms
                    requireBytes(bytes, pos, 2, id)
                    val pauseMs = le16(bytes, pos)
                    pos += 2
                    blocks.add(TzxPause(pauseMs))
                }
                0x21 -> {
                    // Group Start: 1-byte length + ASCII name
                    requireBytes(bytes, pos, 1, id)
                    val nameLen = bytes[pos].toInt() and 0xFF
                    pos++
                    requireBytes(bytes, pos, nameLen, id)
                    val name = String(bytes, pos, nameLen, Charsets.ISO_8859_1)
                    pos += nameLen
                    blocks.add(TzxGroupStart(name))
                }
                0x22 -> {
                    // Group End: no payload
                    blocks.add(TzxGroupEnd)
                }
                0x30 -> {
                    // Text Description: 1-byte length + ASCII text
                    requireBytes(bytes, pos, 1, id)
                    val textLen = bytes[pos].toInt() and 0xFF
                    pos++
                    requireBytes(bytes, pos, textLen, id)
                    val text = String(bytes, pos, textLen, Charsets.ISO_8859_1)
                    pos += textLen
                    blocks.add(TzxTextDescription(text))
                }
                0x32 -> {
                    // Archive Info: 2-byte total length, then entries
                    requireBytes(bytes, pos, 2, id)
                    val totalLen = le16(bytes, pos)
                    pos += 2
                    requireBytes(bytes, pos, totalLen, id)
                    val end = pos + totalLen
                    val numEntries = bytes[pos].toInt() and 0xFF
                    pos++
                    val entries = mutableListOf<Pair<Int, String>>()
                    repeat(numEntries) {
                        requireBytes(bytes, pos, 2, id)
                        val entryType = bytes[pos].toInt() and 0xFF
                        val entryLen = bytes[pos + 1].toInt() and 0xFF
                        pos += 2
                        requireBytes(bytes, pos, entryLen, id)
                        val entryText = String(bytes, pos, entryLen, Charsets.ISO_8859_1)
                        pos += entryLen
                        entries.add(entryType to entryText)
                    }
                    pos = end // skip any padding per spec
                    blocks.add(TzxArchiveInfo(entries))
                }
                else -> {
                    // Unknown block — use a heuristic to find the block length.
                    // For block IDs >= 0x30 the TZX spec says the first byte(s) encode the body
                    // length. For IDs we don't know we read a 4-byte LE length field (conservative
                    // upper-bound that covers most future block types per TZX 1.20 spec).
                    // If the file is truncated, wrap whatever remains.
                    val remaining = bytes.size - pos
                    // Try to read 4-byte LE length as used by many extension blocks
                    val rawLen =
                        if (remaining >= 4) {
                            le32(bytes, pos).also { pos += 4 }
                        } else {
                            remaining.also { pos = bytes.size }
                        }
                    val available = minOf(rawLen, bytes.size - pos)
                    val raw = bytes.copyOfRange(pos, pos + available)
                    pos += available
                    blocks.add(TzxUnknown(id, raw))
                }
            }
        }
        return TzxTapeFile(blocks)
    }

    private fun requireBytes(bytes: ByteArray, pos: Int, need: Int, blockId: Int) {
        if (pos + need > bytes.size) {
            throw TzxParseException(
                "block 0x${blockId.toString(16).uppercase()} at offset ${pos - 1}: " +
                    "need $need bytes but only ${bytes.size - pos} remain"
            )
        }
    }

    private fun le16(bytes: ByteArray, pos: Int): Int =
        (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)

    private fun le24(bytes: ByteArray, pos: Int): Int =
        (bytes[pos].toInt() and 0xFF) or
            ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 16)

    private fun le32(bytes: ByteArray, pos: Int): Int =
        (bytes[pos].toInt() and 0xFF) or
            ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
            ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
            ((bytes[pos + 3].toInt() and 0xFF) shl 24)
}
