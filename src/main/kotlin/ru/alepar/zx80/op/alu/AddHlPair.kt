package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `ADD HL, rr` — 16-bit add with HL as destination. 11 T-states. Only H, N, C are computed; S, Z,
 * P/V preserved from oldF.
 *
 * Opcodes: 0x09 (BC), 0x19 (DE), 0x29 (HL), 0x39 (SP).
 */
class AddHlPair(private val src: RegPair) : Op {
    override val operandLength = 0
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterAddWord(cpu.hl, src.read(cpu), cpu.f)
        cpu.hl = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "ADD HL, ${src.mnemonic}"
}
