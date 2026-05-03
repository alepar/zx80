package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.pop
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RET cc` — conditional return. 5 T-states not-taken; 11 T-states taken (base 5 + 6 extra). Pop
 * happens only on taken branch.
 *
 * 8 opcodes (one per Condition): C0, C8, D0, D8, E0, E8, F0, F8.
 */
class RetCc(private val cond: Condition) : Op {
    override val operandLength = 0
    override val baseCycles = 5

    override fun execute(cpu: Cpu, mem: Memory) {
        if (cond.test(cpu)) {
            cpu.pc = cpu.pop(mem)
            cpu.tStates += 6 // extra cycles for taken branch
        } else {
            cpu.pc = (cpu.pc + 1) and 0xFFFF
        }
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RET ${cond.mnemonic}"
}
