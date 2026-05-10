package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FrameSchedulerRunFrameTest {
    @Test
    fun `runFrame with EI HALT triggers INT and lands in ISR at 0x0038`() {
        val machine = Spectrum48k()
        // Synthetic ROM image:
        //   0x0000: EI    (0xFB)
        //   0x0001: HALT  (0x76)
        //   ...zeros...
        //   0x0038: NOP   (0x00) — minimal ISR; we don't care what runs as long as PC reaches here
        // loadAt bypasses the ReadOnlyBelow(0x4000) write-guard.
        val rom = ByteArray(0x100)
        rom[0x0000] = 0xFB.toByte()
        rom[0x0001] = 0x76.toByte()
        rom[0x0038] = 0x00 // NOP
        machine.mem.loadAt(0, rom)
        machine.cpu.apply {
            pc = 0x0000
            sp = 0xFFFF
            iff1 = false
            iff2 = false
            im = 1
        }

        machine.scheduler.runFrame()

        // After one frame: tStates = budget (69_888) + IM 1 INT cycles (13) = 69_901.
        // HALT case skips cpu.tStates to budget exactly (no instruction overshoot), then
        // interruptRequest() inside runFrame() adds the 13 IM 1 T-states.
        assertThat(machine.cpu.tStates).isEqualTo(69_888L + 13L)
        // INT cleared halted and pushed the post-HALT address (0x0002).
        assertThat(machine.cpu.halted).isFalse
        assertThat(machine.cpu.sp).isEqualTo(0xFFFD)
        assertThat(machine.mem.readWord(0xFFFD)).isEqualTo(0x0002)
        // PC landed at the ISR entry — runFrame's loop stops at budget, then INT fires and
        // jumps to 0x0038. ISR doesn't execute within this frame; PC stays at 0x0038.
        assertThat(machine.cpu.pc).isEqualTo(0x0038)
    }

    @Test
    fun `runFrame with tight RAM loop fires INT once, pushes loop address`() {
        val machine = Spectrum48k()
        // Tight loop in RAM: JR -2 at 0x4000 (0x18 0xFE).
        machine.mem.write(0x4000, 0x18)
        machine.mem.write(0x4001, 0xFE)
        machine.cpu.apply {
            pc = 0x4000
            sp = 0xFFFF
            iff1 = true
            iff2 = true
            im = 1
        }

        machine.scheduler.runFrame()

        // INT fired exactly once.
        assertThat(machine.cpu.sp).isEqualTo(0xFFFD)
        assertThat(machine.mem.readWord(0xFFFD)).isEqualTo(0x4000)
        // Landed in the IM 1 vector.
        assertThat(machine.cpu.pc).isEqualTo(0x0038)
        // Frame budget consumed (within overshoot tolerance: loop iteration is 12 T-states).
        assertThat(machine.cpu.tStates).isBetween(69_888L + 13, 69_888L + 13 + 22)
    }

    @Test
    fun `runFrame with iff1 false leaves PC and SP unchanged after timing`() {
        val machine = Spectrum48k()
        // NOP loop in RAM at 0x4000 (single 0x00 followed by JR -1 at 0x4001/2: 0x18 0xFD).
        // Simpler: write JR -2 at 0x4000.
        machine.mem.write(0x4000, 0x18)
        machine.mem.write(0x4001, 0xFE)
        machine.cpu.apply {
            pc = 0x4000
            sp = 0xFFFF
            iff1 = false
            iff2 = false
            im = 1
        }

        machine.scheduler.runFrame()

        // iff1=false → INT was NOT taken; nothing pushed.
        assertThat(machine.cpu.sp).isEqualTo(0xFFFF)
        // PC stays at the loop start (JR -2 jumps back to 0x4000 each time).
        assertThat(machine.cpu.pc).isEqualTo(0x4000)
        // No INT t-states added.
        assertThat(machine.cpu.tStates).isBetween(69_888L, 69_888L + 22)
    }
}
