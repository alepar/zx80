package ru.alepar.zx80.machine.tape

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TapParserTest {

    @Test
    fun `empty tap bytes produce empty block list`() {
        val result = TapParser.parseTap(byteArrayOf())
        assertThat(result.blocks).isEmpty()
    }

    @Test
    fun `single block with header flag is parsed correctly`() {
        val data = byteArrayOf(0x00, 0x01, 0x02, 0x03) // flag=0x00 (header), 3 payload bytes
        val tapBytes = TapeFixtureBuilder.buildTap(data)
        val result = TapParser.parseTap(tapBytes)
        assertThat(result.blocks).hasSize(1)
        assertThat(result.blocks[0].data).isEqualTo(data)
    }

    @Test
    fun `single block with data flag is parsed correctly`() {
        val data = byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0xBB.toByte())
        val tapBytes = TapeFixtureBuilder.buildTap(data)
        val result = TapParser.parseTap(tapBytes)
        assertThat(result.blocks).hasSize(1)
        assertThat(result.blocks[0].data).isEqualTo(data)
    }

    @Test
    fun `two blocks are decoded in order`() {
        val block1 = byteArrayOf(0x00, 0x11, 0x22)
        val block2 = byteArrayOf(0xFF.toByte(), 0xAA.toByte())
        val tapBytes = TapeFixtureBuilder.buildTap(block1, block2)
        val result = TapParser.parseTap(tapBytes)
        assertThat(result.blocks).hasSize(2)
        assertThat(result.blocks[0].data).isEqualTo(block1)
        assertThat(result.blocks[1].data).isEqualTo(block2)
    }

    @Test
    fun `three blocks are decoded in order`() {
        val b1 = byteArrayOf(0x00, 0x01)
        val b2 = byteArrayOf(0xFF.toByte(), 0x02)
        val b3 = byteArrayOf(0x00, 0x03, 0x04, 0x05)
        val tapBytes = TapeFixtureBuilder.buildTap(b1, b2, b3)
        val result = TapParser.parseTap(tapBytes)
        assertThat(result.blocks).hasSize(3)
        assertThat(result.blocks[2].data).isEqualTo(b3)
    }

    @Test
    fun `single byte block is decoded`() {
        val data = byteArrayOf(0x00)
        val tapBytes = TapeFixtureBuilder.buildTap(data)
        val result = TapParser.parseTap(tapBytes)
        assertThat(result.blocks).hasSize(1)
        assertThat(result.blocks[0].data).isEqualTo(data)
    }

    @Test
    fun `length prefix truncated at 1 byte throws TapParseException`() {
        // Only 1 byte in file - not enough for the 2-byte length prefix
        assertThatThrownBy { TapParser.parseTap(byteArrayOf(0x01)) }
            .isInstanceOf(TapParseException::class.java)
            .hasMessageContaining("truncated length field")
    }

    @Test
    fun `declared length greater than remaining bytes throws TapParseException`() {
        // Length field says 10 bytes but only 2 follow
        val bytes = byteArrayOf(0x0A, 0x00, 0xAA.toByte(), 0xBB.toByte())
        assertThatThrownBy { TapParser.parseTap(bytes) }
            .isInstanceOf(TapParseException::class.java)
            .hasMessageContaining("declares 10 bytes")
    }

    @Test
    fun `large 256-byte block parsed correctly`() {
        val data = ByteArray(256) { it.toByte() }
        val tapBytes = TapeFixtureBuilder.buildTap(data)
        val result = TapParser.parseTap(tapBytes)
        assertThat(result.blocks[0].data).isEqualTo(data)
    }

    @Test
    fun `parity-correct header and data block pair`() {
        val header =
            TapeFixtureBuilder.tapBlockWithParity(
                0x00,
                byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            )
        val dataBlock =
            TapeFixtureBuilder.tapBlockWithParity(
                0xFF.toByte().toInt(),
                byteArrayOf(0xDE.toByte(), 0xAD.toByte()),
            )
        val tapBytes = TapeFixtureBuilder.buildTap(header, dataBlock)
        val result = TapParser.parseTap(tapBytes)
        assertThat(result.blocks).hasSize(2)
        assertThat(result.blocks[0].data[0]).isEqualTo(0x00.toByte()) // header flag
        assertThat(result.blocks[1].data[0]).isEqualTo(0xFF.toByte()) // data flag
    }
}
