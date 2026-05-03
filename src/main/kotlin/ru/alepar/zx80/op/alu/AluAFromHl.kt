package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `<ALU> A, (HL)` — apply an 8-bit ALU operation between A and the byte at memory[HL]. 8 opcodes
 * (one per AluOp), pattern `10 ooo 110`. 7 T-states.
 */
class AluAFromHl(private val op: AluOp) : Op {
    override val operandLength = 0
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = op.apply(cpu.a, mem.read(cpu.hl), cpu.f)
        if (op.updatesA) cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} A, (HL)"
}
