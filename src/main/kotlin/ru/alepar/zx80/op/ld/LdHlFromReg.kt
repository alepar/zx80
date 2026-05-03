package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `LD (HL), r` — writes register r into memory[HL]. 7 T-states. No flag changes. */
class LdHlFromReg(private val src: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        mem.write(cpu.hl, src.read(cpu))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (HL), ${src.mnemonic}"
}
