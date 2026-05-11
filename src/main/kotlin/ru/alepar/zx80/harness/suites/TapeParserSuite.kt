package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.alepar.zx80.harness.SuiteResult
import ru.alepar.zx80.machine.tape.TapBlock
import ru.alepar.zx80.machine.tape.TapParseException
import ru.alepar.zx80.machine.tape.TapParser
import ru.alepar.zx80.machine.tape.TzxArchiveInfo
import ru.alepar.zx80.machine.tape.TzxGroupEnd
import ru.alepar.zx80.machine.tape.TzxGroupStart
import ru.alepar.zx80.machine.tape.TzxParseException
import ru.alepar.zx80.machine.tape.TzxParser
import ru.alepar.zx80.machine.tape.TzxPause
import ru.alepar.zx80.machine.tape.TzxStandardData
import ru.alepar.zx80.machine.tape.TzxTextDescription
import ru.alepar.zx80.machine.tape.TzxTurboData
import ru.alepar.zx80.machine.tape.TzxUnknown

/**
 * Score harness suite for tape format parsers. Runs ~30 named fixtures covering .tap and .tzx
 * parsing, each producing a boolean pass/fail. No CPU involvement — pure data-layer checks.
 *
 * Details shape: { "checks": [ { "name": "...", "passed": bool }, ... ] }
 */
class TapeParserSuite : Suite {
    override val name: String = "tape-parser"
    override val weight: Double = 0.05

    private data class Check(val name: String, val passed: Boolean)

    override fun run(): SuiteResult {
        val checks = mutableListOf<Check>()

        fun check(name: String, block: () -> Boolean) {
            val ok =
                try {
                    block()
                } catch (_: Exception) {
                    false
                }
            checks.add(Check(name, ok))
        }

        fun checkThrows(name: String, block: () -> Unit) {
            val ok =
                try {
                    block()
                    false
                } catch (_: TapParseException) {
                    true
                } catch (_: TzxParseException) {
                    true
                } catch (_: Exception) {
                    false
                }
            checks.add(Check(name, ok))
        }

        // -- .tap fixtures --
        check("tap-empty") { TapParser.parseTap(byteArrayOf()).blocks.isEmpty() }

        check("tap-single-header-block") {
            val data = byteArrayOf(0x00, 0x01, 0x02)
            val bytes = buildTap(data)
            val r = TapParser.parseTap(bytes)
            r.blocks.size == 1 && r.blocks[0] == TapBlock(data)
        }

        check("tap-single-data-block") {
            val data = byteArrayOf(0xFF.toByte(), 0xAA.toByte())
            val bytes = buildTap(data)
            val r = TapParser.parseTap(bytes)
            r.blocks.size == 1 && r.blocks[0].data[0] == 0xFF.toByte()
        }

        check("tap-two-blocks-order") {
            val b1 = byteArrayOf(0x00, 0x11)
            val b2 = byteArrayOf(0xFF.toByte(), 0x22)
            val r = TapParser.parseTap(buildTap(b1, b2))
            r.blocks.size == 2 &&
                r.blocks[0].data.contentEquals(b1) &&
                r.blocks[1].data.contentEquals(b2)
        }

        check("tap-three-blocks") {
            val b1 = byteArrayOf(0x00)
            val b2 = byteArrayOf(0x01)
            val b3 = byteArrayOf(0x02)
            TapParser.parseTap(buildTap(b1, b2, b3)).blocks.size == 3
        }

        check("tap-256-byte-block") {
            val data = ByteArray(256) { it.toByte() }
            val r = TapParser.parseTap(buildTap(data))
            r.blocks[0].data.contentEquals(data)
        }

        check("tap-single-byte-block") {
            val data = byteArrayOf(0x7F)
            TapParser.parseTap(buildTap(data)).blocks[0].data.contentEquals(data)
        }

        check("tap-large-payload-size-preserved") {
            val data = ByteArray(512) { 0xAB.toByte() }
            val r = TapParser.parseTap(buildTap(data))
            r.blocks[0].data.size == 512
        }

        checkThrows("tap-truncated-length-field") { TapParser.parseTap(byteArrayOf(0x01)) }

        checkThrows("tap-length-exceeds-remaining") {
            TapParser.parseTap(byteArrayOf(0x0A, 0x00, 0x01, 0x02))
        }

        check("tap-parity-header-data-pair") {
            val header = tapBlockWithParity(0x00, ByteArray(10))
            val dataBlock = tapBlockWithParity(0xFF, byteArrayOf(0xAA.toByte()))
            val r = TapParser.parseTap(buildTap(header, dataBlock))
            r.blocks.size == 2 &&
                r.blocks[0].data[0] == 0x00.toByte() &&
                r.blocks[1].data[0] == 0xFF.toByte()
        }

        // -- .tzx header fixtures --
        check("tzx-valid-empty") { TzxParser.parseTzx(buildTzxHeader()).blocks.isEmpty() }

        checkThrows("tzx-bad-magic") {
            val b = buildTzxHeader().also { it[0] = 0x00 }
            TzxParser.parseTzx(b)
        }

        checkThrows("tzx-missing-eof-marker") {
            val b = buildTzxHeader().also { it[7] = 0x00 }
            TzxParser.parseTzx(b)
        }

        checkThrows("tzx-unsupported-major-version") {
            TzxParser.parseTzx(buildTzxHeader(major = 2))
        }

        check("tzx-minor-version-ignored") {
            TzxParser.parseTzx(buildTzxHeader(minor = 0)).blocks.isEmpty()
        }

        checkThrows("tzx-too-short") { TzxParser.parseTzx(byteArrayOf(0x5A, 0x58)) }

        // -- .tzx block fixtures --
        check("tzx-0x10-standard-data") {
            val data = byteArrayOf(0xFF.toByte(), 0x01)
            val r = TzxParser.parseTzx(buildTzx(Blk.Standard(1000, data)))
            val b = r.blocks[0] as TzxStandardData
            b.pauseMs == 1000 && b.data.contentEquals(data)
        }

        check("tzx-0x10-zero-pause") {
            val r = TzxParser.parseTzx(buildTzx(Blk.Standard(0, byteArrayOf(0x00))))
            (r.blocks[0] as TzxStandardData).pauseMs == 0
        }

        check("tzx-0x11-turbo-default-timing") {
            val data = byteArrayOf(0xAA.toByte())
            val r = TzxParser.parseTzx(buildTzx(Blk.Turbo(data)))
            val b = r.blocks[0] as TzxTurboData
            b.pilotPulse == 2168 &&
                b.sync1Pulse == 667 &&
                b.oneBitPulse == 1710 &&
                b.data.contentEquals(data)
        }

        check("tzx-0x11-turbo-custom-timing") {
            val r =
                TzxParser.parseTzx(
                    buildTzx(Blk.Turbo(byteArrayOf(0x55), pilotPulse = 3000, pauseMs = 500))
                )
            val b = r.blocks[0] as TzxTurboData
            b.pilotPulse == 3000 && b.pauseMs == 500
        }

        check("tzx-0x20-pause-duration") {
            val r = TzxParser.parseTzx(buildTzx(Blk.Pause(500)))
            (r.blocks[0] as TzxPause).pauseMs == 500
        }

        check("tzx-0x20-stop-tape") {
            val r = TzxParser.parseTzx(buildTzx(Blk.Pause(0)))
            (r.blocks[0] as TzxPause).pauseMs == 0
        }

        check("tzx-0x21-group-start") {
            val r = TzxParser.parseTzx(buildTzx(Blk.GroupStart("Test Group")))
            (r.blocks[0] as TzxGroupStart).name == "Test Group"
        }

        check("tzx-0x22-group-end") {
            val r = TzxParser.parseTzx(buildTzx(Blk.GroupEnd))
            r.blocks[0] == TzxGroupEnd
        }

        check("tzx-group-start-end-pair") {
            val r = TzxParser.parseTzx(buildTzx(Blk.GroupStart("G"), Blk.GroupEnd))
            r.blocks.size == 2 && r.blocks[1] == TzxGroupEnd
        }

        check("tzx-0x30-text-description") {
            val r = TzxParser.parseTzx(buildTzx(Blk.TextDescription("My tape info")))
            (r.blocks[0] as TzxTextDescription).text == "My tape info"
        }

        check("tzx-0x32-archive-info") {
            val entries = listOf(0 to "Game Name", 1 to "Publisher")
            val r = TzxParser.parseTzx(buildTzx(Blk.ArchiveInfo(entries)))
            val b = r.blocks[0] as TzxArchiveInfo
            b.entries.size == 2 && b.entries[0] == (0 to "Game Name")
        }

        check("tzx-unknown-block-wrapped") {
            val header = buildTzxHeader()
            val unknown = byteArrayOf(0xAA.toByte(), 0x03, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33)
            val r = TzxParser.parseTzx(header + unknown)
            r.blocks.size == 1 && (r.blocks[0] as TzxUnknown).id == 0xAA
        }

        check("tzx-unknown-block-then-known-block") {
            val header = buildTzxHeader()
            val unknown = byteArrayOf(0xAA.toByte(), 0x02, 0x00, 0x00, 0x00, 0x11, 0x22)
            val standardBytes = run {
                val out = java.io.ByteArrayOutputStream()
                Blk.Standard(0, byteArrayOf(0xFF.toByte())).writeTo(out)
                out.toByteArray()
            }
            val r = TzxParser.parseTzx(header + unknown + standardBytes)
            r.blocks.size == 2 && r.blocks[1] is TzxStandardData
        }

        check("tzx-mixed-blocks-order-preserved") {
            val r =
                TzxParser.parseTzx(
                    buildTzx(
                        Blk.TextDescription("Info"),
                        Blk.Standard(1000, byteArrayOf(0x00)),
                        Blk.Pause(100),
                        Blk.Standard(0, byteArrayOf(0xFF.toByte())),
                    )
                )
            r.blocks.size == 4 &&
                r.blocks[0] is TzxTextDescription &&
                r.blocks[1] is TzxStandardData &&
                r.blocks[2] is TzxPause &&
                r.blocks[3] is TzxStandardData
        }

        val passed = checks.count { it.passed }
        val details = buildJsonObject {
            put(
                "checks",
                JsonArray(
                    checks.map { c ->
                        buildJsonObject {
                            put("name", JsonPrimitive(c.name))
                            put("passed", JsonPrimitive(c.passed))
                        }
                            as JsonObject
                    }
                ),
            )
        }
        return SuiteResult(
            name = name,
            weight = weight,
            passed = passed,
            total = checks.size,
            details = details,
        )
    }

    // ---- Internal fixture helpers (avoid pulling test-scope classes into main) ----

    private fun buildTap(vararg blocks: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (data in blocks) {
            out.write(data.size and 0xFF)
            out.write((data.size ushr 8) and 0xFF)
            out.write(data)
        }
        return out.toByteArray()
    }

    private fun tapBlockWithParity(flag: Int, payload: ByteArray): ByteArray {
        var parity = flag
        for (b in payload) parity = parity xor (b.toInt() and 0xFF)
        val data = ByteArray(1 + payload.size + 1)
        data[0] = flag.toByte()
        payload.copyInto(data, 1)
        data[data.size - 1] = parity.toByte()
        return data
    }

    private fun buildTzxHeader(major: Int = 1, minor: Int = 20): ByteArray =
        byteArrayOf(0x5A, 0x58, 0x54, 0x61, 0x70, 0x65, 0x21, 0x1A, major.toByte(), minor.toByte())

    private fun buildTzx(vararg blocks: Blk): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(buildTzxHeader())
        for (b in blocks) b.writeTo(out)
        return out.toByteArray()
    }

    private fun writeLE16(out: java.io.ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    /** Mini block builder nested inside the suite (mirrors TzxFixtureBlock from test scope). */
    private sealed class Blk {
        abstract fun writeTo(out: java.io.ByteArrayOutputStream)

        protected fun le16(out: java.io.ByteArrayOutputStream, v: Int) {
            out.write(v and 0xFF)
            out.write((v ushr 8) and 0xFF)
        }

        class Standard(private val pauseMs: Int, private val data: ByteArray) : Blk() {
            override fun writeTo(out: java.io.ByteArrayOutputStream) {
                out.write(0x10)
                le16(out, pauseMs)
                le16(out, data.size)
                out.write(data)
            }
        }

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
        ) : Blk() {
            override fun writeTo(out: java.io.ByteArrayOutputStream) {
                out.write(0x11)
                le16(out, pilotPulse)
                le16(out, sync1Pulse)
                le16(out, sync2Pulse)
                le16(out, zeroBitPulse)
                le16(out, oneBitPulse)
                le16(out, pilotToneLen)
                out.write(lastByteBits)
                le16(out, pauseMs)
                out.write(data.size and 0xFF)
                out.write((data.size ushr 8) and 0xFF)
                out.write((data.size ushr 16) and 0xFF)
                out.write(data)
            }
        }

        class Pause(private val pauseMs: Int) : Blk() {
            override fun writeTo(out: java.io.ByteArrayOutputStream) {
                out.write(0x20)
                le16(out, pauseMs)
            }
        }

        class GroupStart(private val name: String) : Blk() {
            override fun writeTo(out: java.io.ByteArrayOutputStream) {
                out.write(0x21)
                val nb = name.toByteArray(Charsets.ISO_8859_1)
                out.write(nb.size)
                out.write(nb)
            }
        }

        object GroupEnd : Blk() {
            override fun writeTo(out: java.io.ByteArrayOutputStream) {
                out.write(0x22)
            }
        }

        class TextDescription(private val text: String) : Blk() {
            override fun writeTo(out: java.io.ByteArrayOutputStream) {
                out.write(0x30)
                val tb = text.toByteArray(Charsets.ISO_8859_1)
                out.write(tb.size)
                out.write(tb)
            }
        }

        class ArchiveInfo(private val entries: List<Pair<Int, String>>) : Blk() {
            override fun writeTo(out: java.io.ByteArrayOutputStream) {
                out.write(0x32)
                val bodyLen =
                    1 + entries.sumOf { (_, t) -> 2 + t.toByteArray(Charsets.ISO_8859_1).size }
                le16(out, bodyLen)
                out.write(entries.size)
                for ((type, text) in entries) {
                    val tb = text.toByteArray(Charsets.ISO_8859_1)
                    out.write(type)
                    out.write(tb.size)
                    out.write(tb)
                }
            }
        }
    }
}
