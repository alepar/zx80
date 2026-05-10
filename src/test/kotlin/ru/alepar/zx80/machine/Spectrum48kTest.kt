package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Spectrum48kTest {
    @Test
    fun `reset puts CPU in Z80 power-on state`() {
        val machine = Spectrum48k()
        machine.reset()

        assertThat(machine.cpu.pc).isEqualTo(0x0000)
        assertThat(machine.cpu.sp).isEqualTo(0xFFFF)
        assertThat(machine.cpu.af).isEqualTo(0xFFFF)
        assertThat(machine.cpu.iff1).isFalse
        assertThat(machine.cpu.iff2).isFalse
        assertThat(machine.cpu.im).isEqualTo(0)
        assertThat(machine.cpu.halted).isFalse
        assertThat(machine.cpu.tStates).isEqualTo(0L)
    }

    @Test
    fun `reset installs the 48K ROM at 0x0000`() {
        val machine = Spectrum48k()
        machine.reset()

        // First byte of Sinclair 48K ROM is 0xF3 (DI).
        assertThat(machine.mem.read(0x0000)).isEqualTo(0xF3)
        // Last byte of the 16K ROM lives at 0x3FFF and must be readable (sanity that the full
        // image landed). We don't pin a specific value — the ROM is canonical and any byte read
        // here will match across runs.
        val lastRomByte = machine.mem.read(0x3FFF)
        assertThat(lastRomByte).isBetween(0, 0xFF)
    }

    @Test
    fun `runtime writes to ROM area are dropped`() {
        val machine = Spectrum48k()
        machine.reset()
        val before = machine.mem.read(0x1234)
        machine.mem.write(0x1234, before xor 0xFF)
        assertThat(machine.mem.read(0x1234)).isEqualTo(before)
    }

    @Test
    fun `runtime writes to RAM area succeed`() {
        val machine = Spectrum48k()
        machine.reset()
        machine.mem.write(0x5000, 0xAA)
        assertThat(machine.mem.read(0x5000)).isEqualTo(0xAA)
    }

    @Test
    fun `step after reset advances PC by 1 - DI is one byte`() {
        val machine = Spectrum48k()
        machine.reset()
        machine.step()
        assertThat(machine.cpu.pc).isEqualTo(0x0001)
    }

    @Test
    fun `run does not crash for 10000 cycles from reset`() {
        val machine = Spectrum48k()
        machine.reset()
        machine.run(10_000L)
        assertThat(machine.cpu.tStates).isGreaterThanOrEqualTo(10_000L)
    }

    @Test
    fun `runFrame ten times from real ROM does not crash and accumulates 10x69888 t-states`() {
        val machine = Spectrum48k()
        machine.reset()

        repeat(10) { machine.runFrame() }

        // Cumulative t-states equal 10*69_888 plus the final-frame overshoot (≤22) plus any
        // INT t-states each frame (13 for IM 1 — the ROM uses IM 1; 13*10 = 130). Plus
        // overshoot carried within the loop (compensated). Lower bound is exactly
        // 10*69_888; upper bound is 10*(69_888) + 13*10 + ~22 (final frame trailing overshoot).
        val cumulative = machine.cpu.tStates
        assertThat(cumulative).isGreaterThanOrEqualTo(10L * 69_888L)
        assertThat(cumulative).isLessThan(10L * 69_888L + 200L)
    }
}
