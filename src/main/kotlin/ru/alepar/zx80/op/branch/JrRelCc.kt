package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JR cc, e` — conditional relative jump. Only 4 conditions valid (NZ, Z, NC, C). 7 T-states
 * not-taken; 12 T-states taken (base 7 + 5 extra). Always reads displacement from pc+1.
 *
 * 4 opcodes: 0x20 (NZ), 0x28 (Z), 0x30 (NC), 0x38 (C).
 */
class JrRelCc(private val cond: Condition) : Op {
    init {
        require(cond in setOf(Condition.NZ, Condition.Z, Condition.NC, Condition.C)) {
            "JR cc accepts only NZ/Z/NC/C; got $cond"
        }
    }

    override val operandLength = 1
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        val e = mem.read(cpu.pc + 1).toByte().toInt()
        if (cond.test(cpu)) {
            cpu.pc = (cpu.pc + 2 + e) and 0xFFFF
            cpu.tStates += 5 // extra cycles for the taken branch
        } else {
            cpu.pc = (cpu.pc + 2) and 0xFFFF
        }
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JR ${cond.mnemonic}, e"
}
