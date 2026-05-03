package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `IN (C)` — read I/O port BC for flags only (no destination). ED 70 (rrr=110 case). 12 T-states.
 * R+=2. PC+=2. Same flag rules as InRC.
 */
object InCFlags : Op {
    override val operandLength = 0
    override val baseCycles = 12

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.memptr = (cpu.bc + 1) and 0xFFFF
        val byte = cpu.io.read(cpu.bc) and 0xFF
        var f = cpu.f and Flags.C
        if (byte == 0) f = f or Flags.Z
        if (byte and 0x80 != 0) f = f or Flags.S
        if (Flags.parity(byte)) f = f or Flags.PV
        f = f or (byte and 0x28)
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "IN (C)"
}
