package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `LD SP, IX/IY` — copy idx into SP. 10 T-states. R+=2. PC+=2. No flag changes. */
class LdSpFromIx(private val idx: IndexReg) : Op {
    override val operandLength = 0
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.sp = idx.read(cpu)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD SP, ${idx.mnemonic}"
}
