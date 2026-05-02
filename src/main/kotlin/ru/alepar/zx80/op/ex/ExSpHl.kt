package ru.alepar.zx80.op.ex

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object ExSpHl : Op {
    override val operandLength = 0
    override val baseCycles = 19

    override fun execute(cpu: Cpu, mem: Memory) {
        val low = mem.read(cpu.sp)
        val high = mem.read(cpu.sp + 1)
        mem.write(cpu.sp, cpu.l)
        mem.write(cpu.sp + 1, cpu.h)
        cpu.l = low
        cpu.h = high
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EX (SP), HL"
}
