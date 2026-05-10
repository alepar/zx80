package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FrameSchedulerInterruptTest {
    @Test
    fun `interruptRequest is ignored when iff1 is false`() {
        val machine = Spectrum48k()
        machine.cpu.apply { pc = 0x1234; sp = 0xFFFF; iff1 = false; tStates = 0L }
        val scheduler = FrameScheduler(machine)

        val taken = scheduler.interruptRequest()

        assertThat(taken).isFalse
        assertThat(machine.cpu.pc).isEqualTo(0x1234)
        assertThat(machine.cpu.sp).isEqualTo(0xFFFF)
        assertThat(machine.cpu.tStates).isEqualTo(0L)
    }

    @Test
    fun `interruptRequest is ignored during post-EI delay slot`() {
        val machine = Spectrum48k()
        machine.cpu.apply {
            pc = 0x1234; sp = 0xFFFF; iff1 = true; eiPending = true; tStates = 0L
        }
        val scheduler = FrameScheduler(machine)

        val taken = scheduler.interruptRequest()

        assertThat(taken).isFalse
        assertThat(machine.cpu.pc).isEqualTo(0x1234)
        assertThat(machine.cpu.tStates).isEqualTo(0L)
    }

    @Test
    fun `IM 1 acknowledge pushes pc, sets pc to 0x0038, t-states += 13`() {
        val machine = Spectrum48k()
        machine.cpu.apply {
            pc = 0x1234; sp = 0xFFFF; iff1 = true; iff2 = true
            im = 1; tStates = 0L; r = 0
        }
        val scheduler = FrameScheduler(machine)

        val taken = scheduler.interruptRequest()

        assertThat(taken).isTrue
        assertThat(machine.cpu.pc).isEqualTo(0x0038)
        assertThat(machine.cpu.sp).isEqualTo(0xFFFD)
        assertThat(machine.mem.readWord(0xFFFD)).isEqualTo(0x1234)
        assertThat(machine.cpu.iff1).isFalse
        assertThat(machine.cpu.iff2).isFalse
        assertThat(machine.cpu.tStates).isEqualTo(13L)
        assertThat(machine.cpu.r).isEqualTo(1)
        assertThat(machine.cpu.memptr).isEqualTo(0x0038)
    }

    @Test
    fun `IM 0 collapses to IM 1 on Spectrum bus`() {
        // Spectrum's data bus floats to 0xFF during INT acknowledge; opcode 0xFF = RST 38h,
        // which is identical destination and timing to IM 1 (PC=0x0038, +13 T-states).
        val machine = Spectrum48k()
        machine.cpu.apply {
            pc = 0x1234; sp = 0xFFFF; iff1 = true; im = 0; tStates = 0L; r = 0
        }
        val scheduler = FrameScheduler(machine)

        val taken = scheduler.interruptRequest()

        assertThat(taken).isTrue
        assertThat(machine.cpu.pc).isEqualTo(0x0038)
        assertThat(machine.cpu.tStates).isEqualTo(13L)
    }

    @Test
    fun `IM 2 reads vector from I I 0xFF and jumps there with 19 t-states`() {
        val machine = Spectrum48k()
        // Vector = (I shl 8) | 0xFF = 0x39FF. Place little-endian 0x4234 at 0x39FF/0x3A00.
        // loadAt bypasses ROM write-guard for the 0x39FF byte.
        machine.mem.loadAt(0x39FF, byteArrayOf(0x34, 0x42))
        machine.cpu.apply {
            pc = 0x1234; sp = 0xFFFF; iff1 = true; im = 2; i = 0x39; tStates = 0L; r = 0
        }
        val scheduler = FrameScheduler(machine)

        val taken = scheduler.interruptRequest()

        assertThat(taken).isTrue
        assertThat(machine.cpu.pc).isEqualTo(0x4234)
        assertThat(machine.cpu.sp).isEqualTo(0xFFFD)
        assertThat(machine.mem.readWord(0xFFFD)).isEqualTo(0x1234)
        assertThat(machine.cpu.tStates).isEqualTo(19L)
        assertThat(machine.cpu.memptr).isEqualTo(0x4234)
    }

    @Test
    fun `IM 1 with halted CPU advances pc past HALT before pushing`() {
        val machine = Spectrum48k()
        // HALT is at 0x4321; halted=true means the CPU has been holding PC there. On INT,
        // PC must advance to 0x4322 BEFORE being pushed, so RET from the ISR resumes after HALT.
        machine.cpu.apply {
            pc = 0x4321; sp = 0xFFFF; iff1 = true; halted = true; im = 1; tStates = 0L; r = 0
        }
        val scheduler = FrameScheduler(machine)

        val taken = scheduler.interruptRequest()

        assertThat(taken).isTrue
        assertThat(machine.cpu.halted).isFalse
        assertThat(machine.cpu.pc).isEqualTo(0x0038)
        assertThat(machine.mem.readWord(0xFFFD)).isEqualTo(0x4322)
    }
}
