package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object Ei : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.iff1 = true
        cpu.iff2 = true
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 1) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EI"
}
