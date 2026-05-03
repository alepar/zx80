package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DEC <IXH|IXL|IYH|IYL>` — undocumented. Decrement one half of IX/IY, updating flags. 8 T-states.
 * R+=2. PC+=2. C flag preserved (matches documented DEC reg).
 */
class DecIxHalf(private val half: IndexHalfReg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterDec(half.read(cpu), cpu.f)
        half.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DEC ${half.mnemonic}"
}
