package ru.alepar.zx80.machine

/**
 * Drives the CPU one Spectrum frame at a time and fires the maskable interrupt at the end. Owns the
 * cycle-budget overshoot state so the long-run average frame is exactly [T_STATES_PER_FRAME]
 * T-states.
 *
 * Spectrum 48K: 3.5 MHz / 50 Hz = 69_888 T-states per frame.
 */
class FrameScheduler(private val machine: Spectrum48k) {

    /** Cycles the previous frame ran past its budget; subtracted from the next frame's budget. */
    private var pendingCycles: Long = 0

    /**
     * Runs the CPU for one Spectrum frame ([T_STATES_PER_FRAME] T-states minus any overshoot
     * carried over from the prior frame), then fires the maskable INT. If the CPU enters HALT
     * mid-frame, jumps T-states forward to the budget — real hardware idles in HALT until INT.
     *
     * Long-run average is exactly [T_STATES_PER_FRAME] T-states per call.
     */
    fun runFrame() {
        val cpu = machine.cpu
        val budget = T_STATES_PER_FRAME - pendingCycles
        val target = cpu.tStates + budget
        while (cpu.tStates < target && !cpu.halted) machine.step()
        if (cpu.halted) cpu.tStates = target
        pendingCycles = cpu.tStates - target
        interruptRequest()
    }

    /**
     * Z80 maskable interrupt acknowledge. Returns true if the INT was taken, false if it was
     * ignored (iff1=false or in post-EI delay slot).
     *
     * On accept:
     * - If halted, clear halted and advance PC past the HALT byte BEFORE pushing.
     * - Push PC onto the stack (SP -= 2).
     * - Clear iff1 and iff2 (Sean Young: maskable INT clears both).
     * - Increment R (real Z80 ticks R during the INT acknowledge cycle).
     * - Dispatch on cpu.im:
     *     - IM 0/1: Spectrum data bus is 0xFF (RST 38h). PC=0x0038, +13 T-states.
     *     - IM 2: vector = (I shl 8) | 0xFF; PC=mem.readWord(vector), +19 T-states.
     * - Update memptr to the new PC.
     */
    fun interruptRequest(): Boolean {
        val cpu = machine.cpu
        val mem = machine.mem
        if (!cpu.iff1 || cpu.eiPending) return false
        if (cpu.halted) {
            cpu.halted = false
            cpu.pc = (cpu.pc + 1) and 0xFFFF
        }
        cpu.sp = (cpu.sp - 2) and 0xFFFF
        mem.writeWord(cpu.sp, cpu.pc)
        cpu.iff1 = false
        cpu.iff2 = false
        cpu.bumpR(1)
        when (cpu.im) {
            0,
            1 -> {
                cpu.pc = 0x0038
                cpu.tStates += 13
            }
            2 -> {
                val vector = ((cpu.i shl 8) or 0xFF) and 0xFFFF
                cpu.pc = mem.readWord(vector)
                cpu.tStates += 19
            }
        }
        cpu.memptr = cpu.pc
        return true
    }

    companion object {
        const val T_STATES_PER_FRAME = 69_888L
    }
}
