package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `INC rr` — 16-bit increment of a register pair. 6 T-states. **Does NOT affect flags** (unlike
 * 8-bit INC r).
 *
 * Opcodes: 0x03 (BC), 0x13 (DE), 0x23 (HL), 0x33 (SP).
 */
class IncPair(private val dst: RegPair) : Op {
    override val operandLength = 0
    override val baseCycles = 6

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, dst.read(cpu) + 1)
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "INC ${dst.mnemonic}"
}
