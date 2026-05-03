package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (IX+d), r` / `LD (IY+d), r` — write register r to memory at idx+d. 19 T-states. R+=2. PC+=3.
 * No flags.
 */
class LdIxdFromReg(private val idx: IndexReg, private val src: Reg) : Op {
    override val operandLength = 1
    override val baseCycles = 19

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        mem.write(addr, src.read(cpu))
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (${idx.mnemonic}+d), ${src.mnemonic}"
}
