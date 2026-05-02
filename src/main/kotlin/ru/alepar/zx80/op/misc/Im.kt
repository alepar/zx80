package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

class Im(private val mode: Int) : Op {
    init {
        require(mode in 0..2) { "IM mode must be 0, 1, or 2; got $mode" }
    }

    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.im = mode
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.r = (cpu.r and 0x80) or ((cpu.r + 2) and 0x7F)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "IM $mode"
}
