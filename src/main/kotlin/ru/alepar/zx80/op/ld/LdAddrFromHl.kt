package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD (nn), HL` — write HL as a 16-bit little-endian word to memory at absolute address nn. Low
 * byte of HL (= L) at nn, high byte (= H) at nn+1. 16 T-states. No flag changes. PC advances by 3.
 */
object LdAddrFromHl : Op {
    override val operandLength = 2
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val addr = mem.readWord(cpu.pc + 1)
        mem.writeWord(addr, cpu.hl)
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD (nn), HL"
}
