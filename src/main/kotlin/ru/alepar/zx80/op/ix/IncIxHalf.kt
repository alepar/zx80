package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `INC <IXH|IXL|IYH|IYL>` — undocumented. Increment one half of IX/IY, updating flags. 8 T-states.
 * R+=2. PC+=2. C flag preserved (matches documented INC reg).
 */
class IncIxHalf(private val half: IndexHalfReg) : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterInc(half.read(cpu), cpu.f)
        half.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "INC ${half.mnemonic}"
}
