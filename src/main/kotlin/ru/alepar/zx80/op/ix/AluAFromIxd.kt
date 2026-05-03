package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher
import ru.alepar.zx80.op.alu.AluOp

/**
 * `<ALU> A, (IX+d)` / `<ALU> A, (IY+d)` — apply ALU op between A and the byte at idx+d. 19
 * T-states. R+=2. PC+=3. CP doesn't update A.
 */
class AluAFromIxd(private val idx: IndexReg, private val op: AluOp) : Op {
    override val operandLength = 1
    override val baseCycles = 19

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val r = op.apply(cpu.a, mem.read(addr), cpu.f)
        if (op.updatesA) cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} A, (${idx.mnemonic}+d)"
}
