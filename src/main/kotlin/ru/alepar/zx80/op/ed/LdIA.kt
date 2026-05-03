package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD I, A` — copy A into the interrupt vector register I. ED 47. 9 T-states. R+=2. PC+=2. No flag
 * changes.
 */
object LdIA : Op {
    override val operandLength = 0
    override val baseCycles = 9

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.i = cpu.a
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD I, A"
}
