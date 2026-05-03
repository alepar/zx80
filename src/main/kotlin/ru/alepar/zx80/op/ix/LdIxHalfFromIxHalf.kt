package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD <IXH|IXL>, <IXH|IXL>` (DD prefix) or `LD <IYH|IYL>, <IYH|IYL>` (FD prefix) — undocumented.
 * Both operands belong to the same parent IndexReg (DD-prefix only mixes IX halves; FD only mixes
 * IY halves). 8 T-states. R+=2. PC+=2.
 *
 * Constraint that dst.parent == src.parent is enforced at install time, not at runtime.
 */
class LdIxHalfFromIxHalf(private val dst: IndexHalfReg, private val src: IndexHalfReg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, src.read(cpu))
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, ${src.mnemonic}"
}
