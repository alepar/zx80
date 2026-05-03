package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JP cc, nn` — conditional absolute jump. Always reads nn from pc+1..pc+2. 10 T-states whether
 * taken or not. No flag changes.
 *
 * 8 opcodes (one per Condition): C2, CA, D2, DA, E2, EA, F2, FA.
 */
class JpAbsCc(private val cond: Condition) : Op {
    override val operandLength = 2
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        val nn = mem.readWord(cpu.pc + 1)
        cpu.pc = if (cond.test(cpu)) nn else (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JP ${cond.mnemonic}, nn"
}
