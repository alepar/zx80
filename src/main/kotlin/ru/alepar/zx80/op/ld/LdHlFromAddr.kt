package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD HL, (nn)` — read 16-bit word from memory at absolute address nn into HL. Little-endian: low
 * byte at nn → L, high byte at nn+1 → H. 16 T-states. No flag changes. PC advances by 3.
 */
object LdHlFromAddr : Op {
    override val operandLength = 2
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 1)
        cpu.hl = mem.readWord(addr)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD HL, (nn)"
}
