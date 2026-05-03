package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `SBC HL, rr` — 16-bit SBC from HL. ED-prefixed → PC+=2, R+=2. 15 T-states. ALL flags computed; N
 * set.
 *
 * Opcodes: ED 0x42 (BC), ED 0x52 (DE), ED 0x62 (HL), ED 0x72 (SP).
 */
class SbcHlPair(private val src: RegPair) : Op {
    override val operandLength = 0
    override val baseCycles = 15

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterSbcWord(cpu.hl, src.read(cpu), cpu.f)
        cpu.hl = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SBC HL, ${src.mnemonic}"
}
