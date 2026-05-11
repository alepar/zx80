package ru.alepar.zx80.machine

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Dispatcher
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.ReadOnlyBelow
import ru.alepar.zx80.machine.tape.RomTrap
import ru.alepar.zx80.machine.tape.TapeDeck
import ru.alepar.zx80.op.OpTableBuilder

/**
 * Minimal ZX Spectrum 48K machine container — Cpu, Memory with a 0x4000 write-guard, an Op
 * dispatcher, and a 50Hz [FrameScheduler]. cpu.io stays as the default NoIoBus until M2.5 wires the
 * keyboard-aware bus.
 *
 * No display backend, no audio, no contention — those land in M2.3-M2.7.
 *
 * M3 adds [tapeDeck]: a tape mount point. When a tape is loaded the [step] method checks for the
 * Sinclair ROM's LD-BYTES entry (0x0556) before dispatching the normal opcode; if the trap fires,
 * the instruction is replaced by an instant in-memory copy of the next tape block. Empty deck (no
 * tape loaded) leaves the CPU's behaviour identical to pre-M3.
 */
class Spectrum48k(decoder: Decoder = OpTableBuilder.build()) {
    val cpu: Cpu = Cpu()
    val mem: Memory = Memory(ReadOnlyBelow(0x4000))
    private val dispatcher = Dispatcher(decoder)
    val scheduler: FrameScheduler = FrameScheduler(this)

    /** Tape mount point. Construct empty; load via [TapeDeck.loadTape]. */
    val tapeDeck: TapeDeck = TapeDeck()

    /** Z80 power-on register state + ROM installed at 0x0000-0x3FFF. Idempotent. */
    fun reset() {
        cpu.reset()
        mem.loadAt(0, RomLoader.load48k())
    }

    /**
     * Decode and execute one instruction at cpu.pc. Throws if the slot is unmapped.
     *
     * Clears `cpu.eiPending` AFTER executing the next non-EI instruction (the post-EI delay slot
     * mechanism). Capturing the prior value before dispatch ensures the EI step itself doesn't
     * clear the flag it just set.
     *
     * Tape trap: if [tapeDeck] has a trappable block loaded and `cpu.pc == 0x0556`, [RomTrap]
     * consumes the block and synthesises the LD-BYTES return; normal dispatch is skipped.
     */
    fun step() {
        val priorEiPending = cpu.eiPending
        if (RomTrap.tryTrap(cpu, mem, tapeDeck)) {
            if (priorEiPending) cpu.eiPending = false
            return
        }
        val op =
            dispatcher.decodeAt(cpu, mem)
                ?: error(
                    "no dispatch route for opcode 0x${mem.read(cpu.pc).toString(16)} " +
                        "at pc=0x${cpu.pc.toString(16)}"
                )
        op.execute(cpu, mem)
        if (priorEiPending) cpu.eiPending = false
    }

    /**
     * Step until [cycles] T-states have elapsed since the call started, or until the CPU halts.
     * Returns when either condition holds.
     */
    fun run(cycles: Long) {
        val target = cpu.tStates + cycles
        while (cpu.tStates < target && !cpu.halted) step()
    }

    /** Run one Spectrum frame (69_888 T-states) and fire the 50Hz maskable INT at the end. */
    fun runFrame() = scheduler.runFrame()
}
