package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher
import ru.alepar.zx80.op.alu.AluOp

/**
 * `<ALU> A, <IXH|IXL|IYH|IYL>` — undocumented. Apply an 8-bit ALU op between A and one half of
 * IX/IY. 8 T-states. R+=2. PC+=2.
 *
 * Covers 32 opcodes: 8 ALU ops × 4 halves (IXH, IXL under DD; IYH, IYL under FD).
 */
class AluAFromIxHalf(private val op: AluOp, private val src: IndexHalfReg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = op.apply(cpu.a, src.read(cpu), cpu.f)
        if (op.updatesA) cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} A, ${src.mnemonic}"
}
