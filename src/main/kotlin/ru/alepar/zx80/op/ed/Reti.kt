package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.pop
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RETI` — return from maskable interrupt. ED 4D. 14 T-states. R+=2. Pops PC from the stack. On
 * real Z80 it signals end-of-interrupt to peripherals; in our M1 emulator with no peripherals it
 * behaves identically to RETN (restores IFF1 from IFF2). No flag changes.
 */
object Reti : Op {
    override val operandLength = 0
    override val baseCycles = 14

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = cpu.pop(mem)
        cpu.iff1 = cpu.iff2 // NEW: mirror Retn's IFF1 restoration
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RETI"
}
