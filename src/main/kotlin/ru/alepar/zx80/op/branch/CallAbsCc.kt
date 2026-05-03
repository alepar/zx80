package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.push
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `CALL cc, nn` — conditional call. Always reads nn. 10 T-states not taken; 17 T-states taken (base
 * 10 + 7 extra). Push happens only on taken branch.
 *
 * 8 opcodes (one per Condition): C4, CC, D4, DC, E4, EC, F4, FC.
 */
class CallAbsCc(private val cond: Condition) : Op {
    override val operandLength = 2
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        val nn = mem.readWord(cpu.pc + 1)
        if (cond.test(cpu)) {
            val returnAddr = (cpu.pc + 3) and 0xFFFF
            cpu.push(mem, returnAddr)
            cpu.pc = nn
            cpu.tStates += 7 // extra cycles for taken branch
        } else {
            cpu.pc = (cpu.pc + 3) and 0xFFFF
        }
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "CALL ${cond.mnemonic}, nn"
}
