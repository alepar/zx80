package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD A, R` — copy R to A and compute flags from result. ED 5F. 9 T-states. R+=2. PC+=2.
 *
 * R is incremented during the M1 fetch cycles (prefix + opcode) BEFORE the copy stage of the
 * instruction executes. So `cpu.a` ends up with the value of R AFTER the two bumps — if the test
 * sets pre-execute R=0xF1, A will be 0xF3 after LD A,R.
 *
 * Flags: S = bit 7 of A; Z = A == 0; H = 0; P/V = cpu.iff2; N = 0; C preserved. X/Y from A.
 */
object LdAR : Op {
    override val operandLength = 0
    override val baseCycles = 9

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.bumpR(2) // R bumped during fetch, BEFORE the copy
        cpu.a = cpu.r and 0xFF
        var f = cpu.f and Flags.C
        if (cpu.a == 0) f = f or Flags.Z
        if (cpu.a and 0x80 != 0) f = f or Flags.S
        if (cpu.iff2) f = f or Flags.PV
        f = f or (cpu.a and 0x28)
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD A, R"
}
