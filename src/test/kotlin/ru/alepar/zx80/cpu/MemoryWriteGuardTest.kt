package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MemoryWriteGuardTest {
    @Test
    fun `default Memory has no guard - writes to 0x0000 succeed`() {
        val mem = Memory()
        mem.write(0x0000, 0x42)
        assertThat(mem.read(0x0000)).isEqualTo(0x42)
    }

    @Test
    fun `ReadOnlyBelow guard drops writes below limit`() {
        val mem = Memory(ReadOnlyBelow(0x4000))
        mem.write(0x1000, 0x42)
        assertThat(mem.read(0x1000)).isEqualTo(0x00)
    }

    @Test
    fun `ReadOnlyBelow guard permits writes at and above limit`() {
        val mem = Memory(ReadOnlyBelow(0x4000))
        mem.write(0x4000, 0xAA)
        mem.write(0x8000, 0xBB)
        mem.write(0xFFFF, 0xCC)
        assertThat(mem.read(0x4000)).isEqualTo(0xAA)
        assertThat(mem.read(0x8000)).isEqualTo(0xBB)
        assertThat(mem.read(0xFFFF)).isEqualTo(0xCC)
    }

    @Test
    fun `loadAt bypasses the guard and installs ROM bytes`() {
        val mem = Memory(ReadOnlyBelow(0x4000))
        mem.loadAt(0x0000, byteArrayOf(0x11, 0x22, 0x33))
        assertThat(mem.read(0x0000)).isEqualTo(0x11)
        assertThat(mem.read(0x0001)).isEqualTo(0x22)
        assertThat(mem.read(0x0002)).isEqualTo(0x33)
    }

    @Test
    fun `writeWord under guard drops both bytes when both addresses are below limit`() {
        val mem = Memory(ReadOnlyBelow(0x4000))
        mem.writeWord(0x0100, 0xABCD)
        assertThat(mem.read(0x0100)).isEqualTo(0x00)
        assertThat(mem.read(0x0101)).isEqualTo(0x00)
    }
}
