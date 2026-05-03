package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `<ALU> A, n` — apply an 8-bit ALU operation between A and an immediate byte. 8 opcodes (one per
 * AluOp), pattern `11 ooo 110`. 7 T-states. PC advances by 2 (opcode + immediate).
 */
class AluAImm(private val op: AluOp) : Op {
    override val operandLength = 1
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read(cpu.pc + 1)
        val r = op.apply(cpu.a, n, cpu.f)
        if (op.updatesA) cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} A, n"
}
