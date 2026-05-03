package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `EX (SP), IX/IY` — exchange idx with the 16-bit value at top of stack. 23 T-states. R+=2. PC+=2.
 * No flag changes.
 */
class ExSpIx(private val idx: IndexReg) : Op {
    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val stackVal = mem.readWord(cpu.sp)
        mem.writeWord(cpu.sp, idx.read(cpu))
        idx.write(cpu, stackVal)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "EX (SP), ${idx.mnemonic}"
}
