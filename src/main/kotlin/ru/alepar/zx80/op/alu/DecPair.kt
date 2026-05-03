package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DEC rr` — 16-bit decrement of a register pair. 6 T-states. **Does NOT affect flags** (unlike
 * 8-bit DEC r).
 *
 * Opcodes: 0x0B (BC), 0x1B (DE), 0x2B (HL), 0x3B (SP).
 */
class DecPair(private val dst: RegPair) : Op {
    override val operandLength = 0
    override val baseCycles = 6

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, dst.read(cpu) - 1)
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DEC ${dst.mnemonic}"
}
