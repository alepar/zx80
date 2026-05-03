package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `OUTI` — block output increment. ED A3. 16 T-states. R+=2. PC+=2.
 *
 * On real Z80: B is decremented BEFORE the port write so the port (which uses BC as address) sees
 * the new B. We follow that ordering. Then mem[HL] is written to port; HL++.
 *
 * Flags per Sean Young's *Undocumented Z80 Documented* (TUZD):
 * - S/Z = from B after decrement (S = bit 7 of B; Z = (B == 0)).
 * - N = bit 7 of byte read from (HL).
 * - With `temp = byte + L_after_update` (i.e. L after HL ++): C = H = (temp > 0xFF).
 * - PV = parity of `((temp and 7) xor B_after)`.
 * - X = bit 5 of B_after; Y = bit 3 of B_after.
 */
object Outi : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = mem.read(cpu.hl)
        cpu.b = (cpu.b - 1) and 0xFF
        cpu.io.write(cpu.bc, byte)
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        val bAfter = cpu.b
        val temp = byte + cpu.l // L after HL update, per FUSE-confirmed Sean Young rule
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

    override fun mnemonic(operands: OperandFetcher) = "OUTI"
}
