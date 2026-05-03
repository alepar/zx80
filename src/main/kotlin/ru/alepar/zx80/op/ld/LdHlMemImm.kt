package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `LD (HL), n` — writes an 8-bit immediate into memory at HL. 10 T-states. No flag changes. */
object LdHlMemImm : Op {
    override val operandLength = 1
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read(cpu.pc + 1)
        mem.write(cpu.hl, n)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (HL), n"
}
