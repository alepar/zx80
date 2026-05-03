package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RLD` — rotate decimal nibble left between A and (HL). ED 6F. 18 T-states. R+=2. PC+=2.
 *
 * Let m = mem[HL]. New mem[HL] = ((m & 0x0F) << 4) | (A & 0x0F). New A = (A & 0xF0) | (m >> 4).
 *
 * Flags: S/Z from new A; P/V = parity(new A); H = 0; N = 0; C preserved.
 */
object Rld : Op {
    override val operandLength = 0
    override val baseCycles = 18

    override fun execute(cpu: Cpu, mem: Memory) {
        val m = mem.read(cpu.hl)
        val newM = ((m and 0x0F) shl 4) or (cpu.a and 0x0F)
        val newA = (cpu.a and 0xF0) or ((m ushr 4) and 0x0F)
        mem.write(cpu.hl, newM)
        cpu.a = newA and 0xFF
        var f = cpu.f and Flags.C
        if (cpu.a == 0) f = f or Flags.Z
        if (cpu.a and 0x80 != 0) f = f or Flags.S
        if (Flags.parity(cpu.a)) f = f or Flags.PV
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RLD"
}
