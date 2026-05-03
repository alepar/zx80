package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `INI` — block input increment. ED A2. 16 T-states. R+=2. PC+=2.
 *
 * mem[HL] = port[BC]; HL++; B--.
 *
 * Flags per Sean Young's *Undocumented Z80 Documented* (TUZD):
 * - S/Z = from B after decrement (S = bit 7 of B; Z = (B == 0)).
 * - N = bit 7 of byte read.
 * - With `temp = byte + ((C + 1) and 0xFF)`: C = H = (temp > 0xFF).
 * - PV = parity of `((temp and 7) xor B_after)`.
 * - X = bit 5 of B_after; Y = bit 3 of B_after.
 */
object Ini : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = cpu.io.read(cpu.bc) and 0xFF
        mem.write(cpu.hl, byte)
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        cpu.b = (cpu.b - 1) and 0xFF
        val bAfter = cpu.b
        val temp = byte + ((cpu.c + 1) and 0xFF)
        var f = 0
        if (bAfter == 0) f = f or Flags.Z
        if (bAfter and 0x80 != 0) f = f or Flags.S
        if (byte and 0x80 != 0) f = f or Flags.N
        if (temp > 0xFF) f = f or Flags.C or Flags.H
        if (Flags.parity((temp and 0x07) xor bAfter)) f = f or Flags.PV
        f = f or (bAfter and (Flags.X or Flags.Y))
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "INI"
}
