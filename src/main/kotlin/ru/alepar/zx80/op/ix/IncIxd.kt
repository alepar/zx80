package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `INC (IX+d)` / `INC (IY+d)` — increment byte at idx+d. 23 T-states. R+=2. PC+=3. C flag preserved
 * (per Flags.afterInc).
 */
class IncIxd(private val idx: IndexReg) : Op {
    override val operandLength = 1
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val r = Flags.afterInc(mem.read(addr), cpu.f)
        mem.write(addr, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "INC (${idx.mnemonic}+d)"
}
