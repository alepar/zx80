package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `<rotate-shift-op> r` — apply a CB-prefixed rotate/shift to an 8-bit register. 8 T-states. R+=2
 * (CB-prefixed). PC+=2.
 *
 * Covers 49 opcodes in the CB 0x00-0x3F block where rrr (low 3 bits) != 110. (HL) variants are
 * handled by RotShiftHl.
 */
class RotShiftReg(private val op: RotateOp, private val src: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = op.apply(src.read(cpu), cpu.f)
        src.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} ${src.mnemonic}"
}
