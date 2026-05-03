package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD IX/IY, nn` — load 16-bit immediate into index register. 14 T-states. R+=2. PC+=4 (DD/FD
 * prefix + opcode + 2 immediate bytes). No flag changes.
 */
class LdIxImm(private val idx: IndexReg) : Op {
    override val operandLength = 2
    override val baseCycles = 14

    override fun execute(cpu: Cpu, mem: Memory) {
        val nn = mem.readWord(cpu.pc + 2)
        idx.write(cpu, nn)
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${idx.mnemonic}, nn"
}
