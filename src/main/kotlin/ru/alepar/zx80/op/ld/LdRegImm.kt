package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD r, n` — loads an 8-bit immediate into register r. 7 T-states. No flag changes. PC advances by
 * 2 (opcode + immediate byte).
 */
class LdRegImm(private val dst: Reg) : Op {
    override val operandLength = 1
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read(cpu.pc + 1)
        dst.write(cpu, n)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, n"
}
