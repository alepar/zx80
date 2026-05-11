package ru.alepar.zx80.machine.tape

import java.io.ByteArrayOutputStream

/**
 * Programmatic builder for synthetic `.tap` and `.tzx` byte arrays used in tests.
 *
 * Usage:
 * ```
 * val tapBytes = TapeFixtureBuilder.buildTap(byteArrayOf(0x00, 0xAA, 0xAA))
 * val tzxBytes = TapeFixtureBuilder.buildTzx(TzxFixtureBlock.Standard(1000, byteArrayOf(0xFF)))
 * ```
 */
object TapeFixtureBuilder {

    /** Builds a .tap byte array from one or more raw block data arrays. */
    fun buildTap(vararg blocks: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        for (data in blocks) {
            val len = data.size
            out.write(len and 0xFF)
            out.write((len ushr 8) and 0xFF)
            out.write(data)
        }
        return out.toByteArray()
    }

    /** Builds a .tzx byte array with the standard 10-byte header followed by the given blocks. */
    fun buildTzx(vararg blocks: TzxFixtureBlock): ByteArray {
        val out = ByteArrayOutputStream()
        // Header: "ZXTape!" + 0x1A + major=1 + minor=20
        out.write(byteArrayOf(0x5A, 0x58, 0x54, 0x61, 0x70, 0x65, 0x21, 0x1A, 0x01, 0x14))
        for (block in blocks) {
            block.writeTo(out)
        }
        return out.toByteArray()
    }

    /** Builds a .tzx header-only byte array (no blocks). */
    fun buildTzxHeader(major: Int = 1, minor: Int = 20): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x5A, 0x58, 0x54, 0x61, 0x70, 0x65, 0x21, 0x1A))
        out.write(major)
        out.write(minor)
        return out.toByteArray()
    }

    /** Builds a valid .tap block with a correct parity byte appended. */
    fun tapBlockWithParity(flagByte: Int, payload: ByteArray): ByteArray {
        var parity = flagByte
        for (b in payload) parity = parity xor (b.toInt() and 0xFF)
        val data = ByteArray(1 + payload.size + 1)
        data[0] = flagByte.toByte()
        payload.copyInto(data, 1)
        data[data.size - 1] = parity.toByte()
        return data
    }

    /** Computes XOR parity over all bytes of [data]. */
    fun computeParity(data: ByteArray): Int =
        data.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }
}

/** Describes one block in a synthetic .tzx fixture. */
sealed class TzxFixtureBlock {
    abstract fun writeTo(out: ByteArrayOutputStream)

    /** 0x10 Standard Speed Data block. */
    class Standard(private val pauseMs: Int, private val data: ByteArray) : TzxFixtureBlock() {
        override fun writeTo(out: ByteArrayOutputStream) {
            out.write(0x10)
            out.write(pauseMs and 0xFF)
            out.write((pauseMs ushr 8) and 0xFF)
            out.write(data.size and 0xFF)
            out.write((data.size ushr 8) and 0xFF)
            out.write(data)
        }
    }

    /** 0x11 Turbo Speed Data block with standard ZX Spectrum turbo timing defaults. */
    class Turbo(
        private val data: ByteArray,
        private val pilotPulse: Int = 2168,
        private val sync1Pulse: Int = 667,
        private val sync2Pulse: Int = 735,
        private val zeroBitPulse: Int = 855,
        private val oneBitPulse: Int = 1710,
        private val pilotToneLen: Int = 8063,
        private val lastByteBits: Int = 8,
        private val pauseMs: Int = 1000,
    ) : TzxFixtureBlock() {
        override fun writeTo(out: ByteArrayOutputStream) {
            out.write(0x11)
            writeLE16(out, pilotPulse)
            writeLE16(out, sync1Pulse)
            writeLE16(out, sync2Pulse)
            writeLE16(out, zeroBitPulse)
            writeLE16(out, oneBitPulse)
            writeLE16(out, pilotToneLen)
            out.write(lastByteBits)
            writeLE16(out, pauseMs)
            // 3-byte LE data length
            out.write(data.size and 0xFF)
            out.write((data.size ushr 8) and 0xFF)
            out.write((data.size ushr 16) and 0xFF)
            out.write(data)
        }
    }

    /** 0x20 Pause block. [pauseMs] == 0 means "stop the tape". */
    class Pause(private val pauseMs: Int) : TzxFixtureBlock() {
        override fun writeTo(out: ByteArrayOutputStream) {
            out.write(0x20)
            writeLE16(out, pauseMs)
        }
    }

    /** 0x21 Group Start. */
    class GroupStart(private val name: String) : TzxFixtureBlock() {
        override fun writeTo(out: ByteArrayOutputStream) {
            out.write(0x21)
            val nameBytes = name.toByteArray(Charsets.ISO_8859_1)
            out.write(nameBytes.size)
            out.write(nameBytes)
        }
    }

    /** 0x22 Group End. */
    object GroupEnd : TzxFixtureBlock() {
        override fun writeTo(out: ByteArrayOutputStream) {
            out.write(0x22)
        }
    }

    /** 0x30 Text Description. */
    class TextDescription(private val text: String) : TzxFixtureBlock() {
        override fun writeTo(out: ByteArrayOutputStream) {
            out.write(0x30)
            val textBytes = text.toByteArray(Charsets.ISO_8859_1)
            out.write(textBytes.size)
            out.write(textBytes)
        }
    }

    /** 0x32 Archive Info with a list of (type, text) entries. */
    class ArchiveInfo(private val entries: List<Pair<Int, String>>) : TzxFixtureBlock() {
        override fun writeTo(out: ByteArrayOutputStream) {
            out.write(0x32)
            // Compute body length: 1 (count) + sum of (2 + textLen) per entry
            val bodyLen =
                1 + entries.sumOf { (_, t) -> 2 + t.toByteArray(Charsets.ISO_8859_1).size }
            writeLE16(out, bodyLen)
            out.write(entries.size)
            for ((type, text) in entries) {
                val textBytes = text.toByteArray(Charsets.ISO_8859_1)
                out.write(type)
                out.write(textBytes.size)
                out.write(textBytes)
            }
        }
    }

    protected fun writeLE16(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }
}
