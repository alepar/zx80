package ru.alepar.zx80.op.ex

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object ExDeHl : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = cpu.d
        val e = cpu.e
        cpu.d = cpu.h
        cpu.e = cpu.l
        cpu.h = d
        cpu.l = e
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EX DE, HL"
}
