package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD dst, src` — copies an 8-bit register into another. The 49 reg-to-reg combinations populate
 * the 0x40-0x7F block (minus the (HL) variants and the HALT slot at 0x76). 4 T-states. No flag
 * changes.
 */
class LdRegReg(private val src: Reg, private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, src.read(cpu))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, ${src.mnemonic}"
}
