package ru.alepar.zx80.machine

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Dispatcher
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.ReadOnlyBelow
import ru.alepar.zx80.op.OpTableBuilder

/**
 * Minimal ZX Spectrum 48K machine container — Cpu, Memory with a 0x4000 write-guard, and an Op
 * dispatcher. cpu.io stays as the default NoIoBus until M2.5 wires the keyboard-aware bus.
 *
 * No frame loop, no interrupts, no ULA video — those land in M2.2-M2.5.
 */
class Spectrum48k(decoder: Decoder = OpTableBuilder.build()) {
    val cpu: Cpu = Cpu()
    val mem: Memory = Memory(ReadOnlyBelow(0x4000))
    private val dispatcher = Dispatcher(decoder)

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
     */
    fun step() {
        val priorEiPending = cpu.eiPending
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
}
