package ru.alepar.zx80.machine.tape

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TzxParserTest {

    // ---- Header validation ----

    @Test
    fun `valid header with no blocks parses to empty block list`() {
        val bytes = TapeFixtureBuilder.buildTzxHeader()
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).isEmpty()
    }

    @Test
    fun `wrong magic byte throws TzxParseException`() {
        val bytes = TapeFixtureBuilder.buildTzxHeader()
        val corrupted = bytes.copyOf()
        corrupted[0] = 0x00
        assertThatThrownBy { TzxParser.parseTzx(corrupted) }
            .isInstanceOf(TzxParseException::class.java)
            .hasMessageContaining("invalid TZX magic")
    }

    @Test
    fun `missing EOF marker throws TzxParseException`() {
        val bytes = TapeFixtureBuilder.buildTzxHeader()
        val corrupted = bytes.copyOf()
        corrupted[7] = 0x00 // should be 0x1A
        assertThatThrownBy { TzxParser.parseTzx(corrupted) }
            .isInstanceOf(TzxParseException::class.java)
            .hasMessageContaining("0x1A")
    }

    @Test
    fun `unsupported major version throws TzxParseException`() {
        val bytes = TapeFixtureBuilder.buildTzxHeader(major = 2)
        assertThatThrownBy { TzxParser.parseTzx(bytes) }
            .isInstanceOf(TzxParseException::class.java)
            .hasMessageContaining("unsupported TZX major version 2")
    }

    @Test
    fun `minor version variation is accepted`() {
        // minor version 0, 20, 99 — all should parse fine
        val bytes = TapeFixtureBuilder.buildTzxHeader(major = 1, minor = 0)
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).isEmpty()
    }

    @Test
    fun `file shorter than 10 bytes throws TzxParseException`() {
        assertThatThrownBy { TzxParser.parseTzx(byteArrayOf(0x5A, 0x58)) }
            .isInstanceOf(TzxParseException::class.java)
            .hasMessageContaining("too short")
    }

    // ---- 0x10 Standard Speed Data ----

    @Test
    fun `0x10 block decoded with correct pause and data`() {
        val data = byteArrayOf(0xFF.toByte(), 0x01, 0x02)
        val bytes = TapeFixtureBuilder.buildTzx(TzxFixtureBlock.Standard(1000, data))
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).hasSize(1)
        val block = result.blocks[0] as TzxStandardData
        assertThat(block.pauseMs).isEqualTo(1000)
        assertThat(block.data).isEqualTo(data)
    }

    @Test
    fun `0x10 block with zero pause is decoded`() {
        val data = byteArrayOf(0x00)
        val bytes = TapeFixtureBuilder.buildTzx(TzxFixtureBlock.Standard(0, data))
        val result = TzxParser.parseTzx(bytes)
        val block = result.blocks[0] as TzxStandardData
        assertThat(block.pauseMs).isEqualTo(0)
    }

    // ---- 0x11 Turbo Speed Data ----

    @Test
    fun `0x11 block decoded with all timing fields`() {
        val data = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
        val bytes = TapeFixtureBuilder.buildTzx(TzxFixtureBlock.Turbo(data))
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).hasSize(1)
        val block = result.blocks[0] as TzxTurboData
        assertThat(block.pilotPulse).isEqualTo(2168)
        assertThat(block.sync1Pulse).isEqualTo(667)
        assertThat(block.sync2Pulse).isEqualTo(735)
        assertThat(block.zeroBitPulse).isEqualTo(855)
        assertThat(block.oneBitPulse).isEqualTo(1710)
        assertThat(block.pilotToneLen).isEqualTo(8063)
        assertThat(block.lastByteBits).isEqualTo(8)
        assertThat(block.pauseMs).isEqualTo(1000)
        assertThat(block.data).isEqualTo(data)
    }

    @Test
    fun `0x11 block with custom timing values is decoded`() {
        val data = byteArrayOf(0x55)
        val bytes =
            TapeFixtureBuilder.buildTzx(
                TzxFixtureBlock.Turbo(
                    data,
                    pilotPulse = 3000,
                    sync1Pulse = 500,
                    sync2Pulse = 600,
                    zeroBitPulse = 900,
                    oneBitPulse = 1800,
                    pilotToneLen = 4000,
                    lastByteBits = 7,
                    pauseMs = 500,
                )
            )
        val result = TzxParser.parseTzx(bytes)
        val block = result.blocks[0] as TzxTurboData
        assertThat(block.pilotPulse).isEqualTo(3000)
        assertThat(block.lastByteBits).isEqualTo(7)
        assertThat(block.pauseMs).isEqualTo(500)
    }

    // ---- 0x20 Pause ----

    @Test
    fun `0x20 pause block decoded with correct duration`() {
        val bytes = TapeFixtureBuilder.buildTzx(TzxFixtureBlock.Pause(500))
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).hasSize(1)
        val block = result.blocks[0] as TzxPause
        assertThat(block.pauseMs).isEqualTo(500)
    }

    @Test
    fun `0x20 pause with pauseMs zero means stop-the-tape`() {
        val bytes = TapeFixtureBuilder.buildTzx(TzxFixtureBlock.Pause(0))
        val result = TzxParser.parseTzx(bytes)
        val block = result.blocks[0] as TzxPause
        assertThat(block.pauseMs).isEqualTo(0)
    }

    // ---- 0x21 Group Start ----

    @Test
    fun `0x21 group-start block decoded with correct name`() {
        val bytes = TapeFixtureBuilder.buildTzx(TzxFixtureBlock.GroupStart("Level 1"))
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).hasSize(1)
        val block = result.blocks[0] as TzxGroupStart
        assertThat(block.name).isEqualTo("Level 1")
    }

    // ---- 0x22 Group End ----

    @Test
    fun `0x22 group-end block decoded`() {
        val bytes = TapeFixtureBuilder.buildTzx(TzxFixtureBlock.GroupEnd)
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).hasSize(1)
        assertThat(result.blocks[0]).isEqualTo(TzxGroupEnd)
    }

    @Test
    fun `group-start and group-end round-trip`() {
        val bytes =
            TapeFixtureBuilder.buildTzx(
                TzxFixtureBlock.GroupStart("MyGroup"),
                TzxFixtureBlock.Standard(0, byteArrayOf(0xFF.toByte())),
                TzxFixtureBlock.GroupEnd,
            )
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).hasSize(3)
        assertThat(result.blocks[0]).isInstanceOf(TzxGroupStart::class.java)
        assertThat(result.blocks[2]).isEqualTo(TzxGroupEnd)
    }

    // ---- 0x30 Text Description ----

    @Test
    fun `0x30 text-description block decoded`() {
        val bytes = TapeFixtureBuilder.buildTzx(TzxFixtureBlock.TextDescription("Hello World"))
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).hasSize(1)
        val block = result.blocks[0] as TzxTextDescription
        assertThat(block.text).isEqualTo("Hello World")
    }

    // ---- 0x32 Archive Info ----

    @Test
    fun `0x32 archive-info block decoded with entries`() {
        val entries = listOf(0 to "My Game", 1 to "My Publisher")
        val bytes = TapeFixtureBuilder.buildTzx(TzxFixtureBlock.ArchiveInfo(entries))
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).hasSize(1)
        val block = result.blocks[0] as TzxArchiveInfo
        assertThat(block.entries).hasSize(2)
        assertThat(block.entries[0]).isEqualTo(0 to "My Game")
        assertThat(block.entries[1]).isEqualTo(1 to "My Publisher")
    }

    // ---- Unknown blocks ----

    @Test
    fun `unknown block id is wrapped in TzxUnknown and parser continues`() {
        // We'll build a raw TZX with a known block before and after an "unknown" block ID 0x99
        // The unknown block will have a 4-byte body length of 3, followed by 3 payload bytes
        val standard = TzxFixtureBlock.Standard(1000, byteArrayOf(0xFF.toByte()))
        val headerBytes = TapeFixtureBuilder.buildTzxHeader()
        val standardBytes = run {
            val out = java.io.ByteArrayOutputStream()
            standard.writeTo(out)
            out.toByteArray()
        }
        // Manually construct an unknown block 0xAA with 4-byte length prefix = 3, then 3 bytes
        val unknownBlock = byteArrayOf(0xAA.toByte(), 0x03, 0x00, 0x00, 0x00, 0x11, 0x22, 0x33)
        val combined = headerBytes + standardBytes + unknownBlock + standardBytes
        val result = TzxParser.parseTzx(combined)
        assertThat(result.blocks).hasSize(3)
        assertThat(result.blocks[0]).isInstanceOf(TzxStandardData::class.java)
        assertThat(result.blocks[1]).isInstanceOf(TzxUnknown::class.java)
        val unknown = result.blocks[1] as TzxUnknown
        assertThat(unknown.id).isEqualTo(0xAA)
        assertThat(result.blocks[2]).isInstanceOf(TzxStandardData::class.java)
    }

    // ---- Multi-block tape ----

    @Test
    fun `mixed block types all decoded in order`() {
        val bytes =
            TapeFixtureBuilder.buildTzx(
                TzxFixtureBlock.TextDescription("Info"),
                TzxFixtureBlock.Standard(1000, byteArrayOf(0x00, 0x01)),
                TzxFixtureBlock.Pause(100),
                TzxFixtureBlock.Standard(0, byteArrayOf(0xFF.toByte(), 0x02)),
            )
        val result = TzxParser.parseTzx(bytes)
        assertThat(result.blocks).hasSize(4)
        assertThat(result.blocks[0]).isInstanceOf(TzxTextDescription::class.java)
        assertThat(result.blocks[1]).isInstanceOf(TzxStandardData::class.java)
        assertThat(result.blocks[2]).isInstanceOf(TzxPause::class.java)
        assertThat(result.blocks[3]).isInstanceOf(TzxStandardData::class.java)
    }
}
