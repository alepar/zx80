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
 * Documented flags: Z = (B == 0), N = 1. Other flags (S, H, P/V) have undocumented dependence on
 * byte+C value; we leave them at 0.
 */
object Ini : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = cpu.io.read(cpu.bc) and 0xFF
        mem.write(cpu.hl, byte)
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        cpu.b = (cpu.b - 1) and 0xFF
        var f = Flags.N
        if (cpu.b == 0) f = f or Flags.Z
        // X/Y from n = (byte + ((C+1) and 0xFF)) and 0xFF per Zilog NMOS
        val n = (byte + ((cpu.c + 1) and 0xFF)) and 0xFF
        f = f or (n and 0x28)
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "INI"
}
