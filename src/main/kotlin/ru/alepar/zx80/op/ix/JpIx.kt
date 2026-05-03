package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JP (IX)` / `JP (IY)` — jump to the value of IX / IY. Despite the parens, this is NOT a memory
 * dereference — pc is set to idx itself. 8 T-states. R+=2. No flag changes.
 */
class JpIx(private val idx: IndexReg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = idx.read(cpu)
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JP (${idx.mnemonic})"
}
