package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.pop
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `POP IX/IY` — pop top of stack into idx. 14 T-states. R+=2. PC+=2. No flag changes. */
class PopIx(private val idx: IndexReg) : Op {
    override val operandLength = 0
    override val baseCycles = 14

    override fun execute(cpu: Cpu, mem: Memory) {
        idx.write(cpu, cpu.pop(mem))
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "POP ${idx.mnemonic}"
}
