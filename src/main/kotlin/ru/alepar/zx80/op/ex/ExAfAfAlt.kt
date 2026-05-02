package ru.alepar.zx80.op.ex

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object ExAfAfAlt : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val a = cpu.a
        val f = cpu.f
        cpu.a = cpu.aAlt
        cpu.f = cpu.fAlt
        cpu.aAlt = a
        cpu.fAlt = f
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EX AF, AF'"
}
