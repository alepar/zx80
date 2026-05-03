package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD A, (BC)` and `LD A, (DE)` — read byte at memory[pair] into A. 7 T-states. No flag changes.
 * Only BC and DE are valid pairs for this op (the (HL) and (SP) variants don't exist in the Z80 ISA
 * at this opcode shape).
 */
class LdAFromBcDe(private val pair: RegPair) : Op {
    init {
        require(pair == RegPair.BC || pair == RegPair.DE) {
            "LdAFromBcDe accepts only BC or DE; got $pair"
        }
    }

    override val operandLength = 0
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.a = mem.read(pair.read(cpu))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD A, (${pair.mnemonic})"
}
