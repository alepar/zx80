package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (IX+d), n` / `LD (IY+d), n` — write 8-bit immediate n to memory at idx+d. 19 T-states. R+=2.
 * PC+=4. No flags.
 *
 * Operand bytes: d at pc+2, n at pc+3.
 */
class LdIxdImm(private val idx: IndexReg) : Op {
    override val operandLength = 2
    override val baseCycles = 19

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val n = mem.read(cpu.pc + 3)
        val addr = (idx.read(cpu) + d) and 0xFFFF
        mem.write(addr, n)
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (${idx.mnemonic}+d), n"
}
