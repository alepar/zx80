package ru.alepar.zx80.op.stack

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.cpu.push
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `PUSH rr` — push a 16-bit register pair onto the Z80 stack. SP is pre-decremented twice (high
 * byte at SP-1, low byte at SP-2 = new SP). 11 T-states. No flag changes.
 *
 * Valid pairs: BC, DE, HL, AF. (SP is rejected — pushing the stack pointer onto the stack itself
 * isn't a valid Z80 operation here.)
 *
 * Opcodes: 0xC5 (BC), 0xD5 (DE), 0xE5 (HL), 0xF5 (AF).
 */
class PushPair(private val src: RegPair) : Op {
    init {
        require(src != RegPair.SP) { "PUSH does not accept SP; valid pairs are BC/DE/HL/AF" }
    }

    override val operandLength = 0
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.push(mem, src.read(cpu))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "PUSH ${src.mnemonic}"
}
