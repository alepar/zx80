package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (nn), IX/IY` — store idx to memory at the 16-bit address nn. 20 T-states. R+=2. PC+=4. No
 * flag changes.
 */
class LdAddrFromIx(private val idx: IndexReg) : Op {
    override val operandLength = 2
    override val baseCycles = 20

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 2)
        mem.writeWord(addr, idx.read(cpu))
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (nn), ${idx.mnemonic}"
}
