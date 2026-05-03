package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `IN r, (C)` — read I/O port BC into register r. ED 40+8*r (r != 6). 12 T-states. R+=2. PC+=2.
 *
 * Flags: S/Z/PV(parity) from byte read; H = 0; N = 0; C preserved.
 */
class InRC(private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 12

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = cpu.io.read(cpu.bc) and 0xFF
        dst.write(cpu, byte)
        var f = cpu.f and Flags.C
        if (byte == 0) f = f or Flags.Z
        if (byte and 0x80 != 0) f = f or Flags.S
        if (Flags.parity(byte)) f = f or Flags.PV
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "IN ${dst.mnemonic}, (C)"
}
