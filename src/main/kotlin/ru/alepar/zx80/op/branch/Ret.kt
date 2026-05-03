package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.pop
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RET` — pop the return address from the stack into pc. 10 T-states. No flag changes. Single
 * opcode 0xC9.
 */
object Ret : Op {
    override val operandLength = 0
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = cpu.pop(mem)
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RET"
}
