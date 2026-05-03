package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `<ALU> A, r` — apply an 8-bit ALU operation between A and a register. Covers the 56 opcodes in
 * the 0x80-0xBF block where rrr (low 3 bits) != 110. 4 T-states. `op.updatesA` controls whether A
 * is written (false only for CP).
 */
class AluAReg(private val op: AluOp, private val src: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = op.apply(cpu.a, src.read(cpu), cpu.f)
        if (op.updatesA) cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} A, ${src.mnemonic}"
}
