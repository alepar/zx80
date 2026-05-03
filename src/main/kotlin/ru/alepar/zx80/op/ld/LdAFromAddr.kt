package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD A, (nn)` — load A from memory at the little-endian 16-bit absolute address. 13 T-states. No
 * flag changes. PC advances by 3.
 */
object LdAFromAddr : Op {
    override val operandLength = 2
    override val baseCycles = 13

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 1)
        cpu.a = mem.read(addr)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD A, (nn)"
}
