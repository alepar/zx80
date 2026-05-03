package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CpuStackTest {

    @Test
    fun `push decrements SP twice and writes high byte then low byte (little-endian)`() {
        val cpu = Cpu().apply { sp = 0x4000 }
        val mem = Memory()
        cpu.push(mem, 0xABCD)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFE)).isEqualTo(0xCD) // low byte at lower address
        assertThat(mem.read(0x3FFF)).isEqualTo(0xAB) // high byte at higher address
    }

    @Test
    fun `pop reads low byte from SP, high byte from SP+1, increments SP twice`() {
        val cpu = Cpu().apply { sp = 0x3FFE }
        val mem =
            Memory().apply {
                write(0x3FFE, 0xCD)
                write(0x3FFF, 0xAB)
            }
        val value = cpu.pop(mem)
        assertThat(value).isEqualTo(0xABCD)
        assertThat(cpu.sp).isEqualTo(0x4000)
    }

    @Test
    fun `push then pop round-trips a value`() {
        val cpu = Cpu().apply { sp = 0x8000 }
        val mem = Memory()
        cpu.push(mem, 0x1234)
        cpu.push(mem, 0x5678)
        assertThat(cpu.pop(mem)).isEqualTo(0x5678)
        assertThat(cpu.pop(mem)).isEqualTo(0x1234)
        assertThat(cpu.sp).isEqualTo(0x8000)
    }

    @Test
    fun `push wraps SP from 0x0000 to 0xFFFE`() {
        val cpu = Cpu().apply { sp = 0x0000 }
        val mem = Memory()
        cpu.push(mem, 0x1234)
        assertThat(cpu.sp).isEqualTo(0xFFFE)
        assertThat(mem.read(0xFFFE)).isEqualTo(0x34)
        assertThat(mem.read(0xFFFF)).isEqualTo(0x12)
    }

    @Test
    fun `pop wraps SP from 0xFFFE to 0x0000`() {
        val cpu = Cpu().apply { sp = 0xFFFE }
        val mem =
            Memory().apply {
                write(0xFFFE, 0x34)
                write(0xFFFF, 0x12)
            }
        val value = cpu.pop(mem)
        assertThat(value).isEqualTo(0x1234)
        assertThat(cpu.sp).isEqualTo(0x0000)
    }

    @Test
    fun `push masks value to 16 bits`() {
        val cpu = Cpu().apply { sp = 0x4000 }
        val mem = Memory()
        cpu.push(mem, 0x1ABCD) // 17 bits; top bit must be discarded
        assertThat(mem.read(0x3FFE)).isEqualTo(0xCD)
        assertThat(mem.read(0x3FFF)).isEqualTo(0xAB)
    }
}
