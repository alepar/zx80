package ru.alepar.zx80.op.stack

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.cpu.pop
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `POP rr` — pop a 16-bit value off the Z80 stack into a register pair. SP is post-incremented
 * twice (low byte from SP, high byte from SP+1). 10 T-states.
 *
 * Valid pairs: BC, DE, HL, AF. (SP is rejected.)
 *
 * Note: POP AF loads F from the popped low byte — flags do change, but are loaded from memory
 * rather than computed.
 *
 * Opcodes: 0xC1 (BC), 0xD1 (DE), 0xE1 (HL), 0xF1 (AF).
 */
class PopPair(private val dst: RegPair) : Op {
    init {
        require(dst != RegPair.SP) { "POP does not accept SP; valid pairs are BC/DE/HL/AF" }
    }

    override val operandLength = 0
    override val baseCycles = 10

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, cpu.pop(mem))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "POP ${dst.mnemonic}"
}
