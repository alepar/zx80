package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MemoryTest {
    @Test
    fun `fresh memory is 64K of zeroes`() {
        val mem = Memory()
        for (addr in 0..0xFFFF) {
            assertThat(mem.read(addr)).`as`("addr=0x%04X", addr).isZero
        }
    }

    @Test
    fun `read returns unsigned byte 0 to 255`() {
        val mem = Memory()
        mem.write(0x1234, 0xFF)
        assertThat(mem.read(0x1234)).isEqualTo(0xFF)

        mem.write(0x0000, 0x80)
        assertThat(mem.read(0x0000)).isEqualTo(0x80)
    }

    @Test
    fun `write masks to lowest 8 bits`() {
        val mem = Memory()
        mem.write(0x100, 0x1FF)
        assertThat(mem.read(0x100)).isEqualTo(0xFF)
    }

    @Test
    fun `addresses wrap modulo 64K`() {
        val mem = Memory()
        mem.write(0x10000, 0x42) // wraps to 0x0000
        assertThat(mem.read(0x0000)).isEqualTo(0x42)
        mem.write(-1, 0x55) // wraps to 0xFFFF
        assertThat(mem.read(0xFFFF)).isEqualTo(0x55)
    }

    @Test
    fun `loadAt copies bytes starting at the given address`() {
        val mem = Memory()
        mem.loadAt(0x100, byteArrayOf(0x11, 0x22, 0x33, 0x44))
        assertThat(mem.read(0x100)).isEqualTo(0x11)
        assertThat(mem.read(0x101)).isEqualTo(0x22)
        assertThat(mem.read(0x102)).isEqualTo(0x33)
        assertThat(mem.read(0x103)).isEqualTo(0x44)
    }

    @Test
    fun `loadAt rejects payload that overflows 64K`() {
        val mem = Memory()
        assertThatThrownBy { mem.loadAt(0xFFFE, byteArrayOf(1, 2, 3)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `loadAt with empty payload is a no-op even at the last valid address`() {
        val mem = Memory()
        mem.loadAt(0xFFFF, byteArrayOf()) // must not throw
        mem.loadAt(0, byteArrayOf())
        // sanity: nothing changed
        assertThat(mem.read(0xFFFF)).isZero
        assertThat(mem.read(0)).isZero
    }

    @Test
    fun `loadAt rejects pathologically large payload using overflow-safe check`() {
        val mem = Memory()
        // Allocate a payload equal in size to the full address space, load at non-zero addr
        // — the naive `addr + payload.size` would compute 0x10100, which fits in Int and would
        // PASS the old (overflowing) check ONLY for huge payloads. Here we simply demonstrate
        // the check still fires correctly under realistic over-large payloads.
        assertThatThrownBy { mem.loadAt(0x100, ByteArray(Memory.SIZE)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("overflows")
    }

    @Test
    fun `readWord reads little-endian 16-bit word at addr (low byte first)`() {
        val mem =
            Memory().apply {
                write(0x100, 0xCD) // low
                write(0x101, 0xAB) // high
            }
        assertThat(mem.readWord(0x100)).isEqualTo(0xABCD)
    }

    @Test
    fun `readWord wraps mod 64K when addr+1 crosses 0xFFFF`() {
        val mem =
            Memory().apply {
                write(0xFFFF, 0xCD)
                write(0x0000, 0xAB)
            }
        assertThat(mem.readWord(0xFFFF)).isEqualTo(0xABCD)
    }

    @Test
    fun `writeWord writes little-endian 16-bit word at addr (low byte first)`() {
        val mem = Memory()
        mem.writeWord(0x100, 0xABCD)
        assertThat(mem.read(0x100)).isEqualTo(0xCD)
        assertThat(mem.read(0x101)).isEqualTo(0xAB)
    }

    @Test
    fun `writeWord masks value to 16 bits`() {
        val mem = Memory()
        mem.writeWord(0x100, 0x1ABCD)
        assertThat(mem.read(0x100)).isEqualTo(0xCD)
        assertThat(mem.read(0x101)).isEqualTo(0xAB)
    }
}
