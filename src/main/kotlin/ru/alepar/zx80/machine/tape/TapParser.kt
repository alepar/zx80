package ru.alepar.zx80.machine.tape

/** Thrown when a .tap file is structurally invalid. */
class TapParseException(message: String) : Exception(message)

/**
 * Parses a raw `.tap` byte array into a [TapTapeFile].
 *
 * The .tap format is a flat sequence of blocks, each prefixed with a 2-byte little-endian length
 * field. An empty file produces an empty block list. If a length field declares more bytes than
 * remain in the file, [TapParseException] is thrown.
 */
object TapParser {
    fun parseTap(bytes: ByteArray): TapTapeFile {
        val blocks = mutableListOf<TapBlock>()
        var pos = 0
        while (pos < bytes.size) {
            if (pos + 2 > bytes.size) {
                throw TapParseException(
                    "truncated length field at offset $pos: need 2 bytes, have ${bytes.size - pos}"
                )
            }
            val len = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8)
            pos += 2
            if (pos + len > bytes.size) {
                throw TapParseException(
                    "block at offset ${pos - 2} declares $len bytes but only ${bytes.size - pos} remain"
                )
            }
            blocks.add(TapBlock(bytes.copyOfRange(pos, pos + len)))
            pos += len
        }
        return TapTapeFile(blocks)
    }
}
