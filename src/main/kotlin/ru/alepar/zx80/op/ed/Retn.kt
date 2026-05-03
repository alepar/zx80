package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.pop
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RETN` — return from non-maskable interrupt. ED 45. 14 T-states. R+=2. Pops PC from the stack and
 * restores IFF1 from IFF2 (the difference from RET). No flag changes.
 */
object Retn : Op {
    override val operandLength = 0
    override val baseCycles = 14

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = cpu.pop(mem)
        cpu.iff1 = cpu.iff2
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RETN"
}
