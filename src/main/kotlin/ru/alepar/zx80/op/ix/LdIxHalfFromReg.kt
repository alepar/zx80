package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD <IXH|IXL|IYH|IYL>, r` — undocumented. Copy a regular 8-bit register into one half of IX/IY. 8
 * T-states. R+=2. PC+=2.
 */
class LdIxHalfFromReg(private val dst: IndexHalfReg, private val src: Reg) : Op {
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
