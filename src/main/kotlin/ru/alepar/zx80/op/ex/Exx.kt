package ru.alepar.zx80.op.ex

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object Exx : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val b = cpu.b
        val c = cpu.c
        val d = cpu.d
        val e = cpu.e
        val h = cpu.h
        val l = cpu.l
        cpu.b = cpu.bAlt
        cpu.c = cpu.cAlt
        cpu.d = cpu.dAlt
        cpu.e = cpu.eAlt
        cpu.h = cpu.hAlt
        cpu.l = cpu.lAlt
        cpu.bAlt = b
        cpu.cAlt = c
        cpu.dAlt = d
        cpu.eAlt = e
        cpu.hAlt = h
        cpu.lAlt = l
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EXX"
}
