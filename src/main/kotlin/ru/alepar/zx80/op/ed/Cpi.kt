package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `CPI` — block compare increment. ED A1. 16 T-states. R+=2. PC+=2.
 *
 * Compare A with mem[HL] (set flags as SUB but don't write A); HL++; BC--.
 *
 * Flags: S/Z/H/N from afterSub(A, mem[HL], 0); P/V = (BC != 0 after); C preserved.
 */
object Cpi : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterSub(cpu.a, mem.read(cpu.hl), 0)
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        cpu.bc = (cpu.bc - 1) and 0xFFFF
        var f = (r.newF and (Flags.S or Flags.Z or Flags.H or Flags.N)) or (cpu.f and Flags.C)
        if (cpu.bc != 0) f = f or Flags.PV
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "CPI"
}
