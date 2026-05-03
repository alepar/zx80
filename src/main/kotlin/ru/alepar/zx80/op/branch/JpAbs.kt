package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JP nn` — unconditional absolute jump. Reads little-endian 16-bit address from pc+1..pc+2, sets
 * pc to it. 10 T-states. No flag changes.
 */
object JpAbs : Op {
    override val operandLength = 2
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = mem.readWord(cpu.pc + 1)
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JP nn"
}
