package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LDI` — block move increment. ED A0. 16 T-states. R+=2. PC+=2.
 *
 * mem[DE] = mem[HL]; HL++; DE++; BC--.
 *
 * Flags: H=0, N=0, P/V = (BC != 0 after); S/Z/C preserved.
 */
object Ldi : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = mem.read(cpu.hl)
        mem.write(cpu.de, byte)
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        cpu.de = (cpu.de + 1) and 0xFFFF
        cpu.bc = (cpu.bc - 1) and 0xFFFF
        var f = cpu.f and (Flags.S or Flags.Z or Flags.C)
        if (cpu.bc != 0) f = f or Flags.PV
        f = f or (((byte + cpu.a) and 0xFF) and 0x28) // X/Y from (byte + A) per Zilog NMOS
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LDI"
}
