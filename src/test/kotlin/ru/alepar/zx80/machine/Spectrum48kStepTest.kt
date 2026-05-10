package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Spectrum48kStepTest {
    @Test
    fun `EI step leaves eiPending true`() {
        val machine = Spectrum48k()
        // EI = 0xFB at 0x0000; loadAt bypasses the ROM write-guard.
        machine.mem.loadAt(0, byteArrayOf(0xFB.toByte()))
        machine.cpu.pc = 0x0000
        machine.cpu.eiPending = false

        machine.step()

        assertThat(machine.cpu.eiPending).isTrue
        assertThat(machine.cpu.iff1).isTrue
        assertThat(machine.cpu.iff2).isTrue
    }

    @Test
    fun `instruction after EI clears eiPending`() {
        val machine = Spectrum48k()
        // EI; NOP at 0x0000. NOP = 0x00.
        machine.mem.loadAt(0, byteArrayOf(0xFB.toByte(), 0x00))
        machine.cpu.pc = 0x0000

        machine.step()
        assertThat(machine.cpu.eiPending).isTrue

        machine.step()
        assertThat(machine.cpu.eiPending).isFalse
    }

    @Test
    fun `consecutive non-EI steps keep eiPending false`() {
        val machine = Spectrum48k()
        // NOP; NOP at 0x0000.
        machine.mem.loadAt(0, byteArrayOf(0x00, 0x00))
        machine.cpu.pc = 0x0000
        machine.cpu.eiPending = false

        machine.step()
        machine.step()

        assertThat(machine.cpu.eiPending).isFalse
    }
}
