package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD A, R` — copy R to A and compute flags from result. ED 5F. 9 T-states. R+=2. PC+=2.
 *
 * Note: R is read BEFORE bumpR(2), per the documented Z80 semantics — you see the value just after
 * the instruction fetch.
 *
 * Flags: S = bit 7 of A; Z = A == 0; H = 0; P/V = cpu.iff2; N = 0; C preserved.
 */
object LdAR : Op {
    override val operandLength = 0
    override val baseCycles = 9

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.a = cpu.r and 0xFF
        var f = cpu.f and Flags.C
        if (cpu.a == 0) f = f or Flags.Z
        if (cpu.a and 0x80 != 0) f = f or Flags.S
        if (cpu.iff2) f = f or Flags.PV
        f = f or (cpu.a and 0x28) // X/Y from result
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD A, R"
}
