package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FrameSchedulerOvershootTest {
    @Test
    fun `three consecutive runFrames accumulate exactly 3x69888 t-states modulo INT cycles`() {
        val machine = Spectrum48k()
        // Tight RAM loop: JR -2 at 0x4000.
        machine.mem.write(0x4000, 0x18)
        machine.mem.write(0x4001, 0xFE)
        machine.cpu.apply { pc = 0x4000; sp = 0xFFFF; iff1 = false; iff2 = false; im = 1 }

        val scheduler = FrameScheduler(machine)
        scheduler.runFrame()
        val afterFrame1 = machine.cpu.tStates
        scheduler.runFrame()
        val afterFrame2 = machine.cpu.tStates
        scheduler.runFrame()
        val afterFrame3 = machine.cpu.tStates

        // First frame may overshoot by up to 11 (JR is 12 T-states, budget 69_888 → overshoot
        // is between 0 and 11 inclusive). Each subsequent frame's budget compensates, so
        // cumulative t-states on the boundary cap each call exactly at multiples of 69_888.
        // With iff1=false, no INT t-states are added.
        // The strict invariant: at the end of any runFrame, tStates is in
        // [N*69_888, N*69_888 + 11] for the N-th frame, AND the running pendingCycles makes
        // the long-run average exactly 69_888 per frame.
        // We verify by checking that frame N's overshoot is strictly bounded:
        assertThat(afterFrame1 - 0L).isBetween(69_888L, 69_888L + 11)
        assertThat(afterFrame2 - afterFrame1).isBetween(69_888L - 11, 69_888L + 11)
        assertThat(afterFrame3 - afterFrame2).isBetween(69_888L - 11, 69_888L + 11)
        // After 3 frames, cumulative is within ±11 of 3*69_888 (residual overshoot of the last
        // frame).
        assertThat(afterFrame3).isBetween(3 * 69_888L, 3 * 69_888L + 11)
    }
}
