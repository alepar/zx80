package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `CPD` — block compare decrement. ED A9. Same as Cpi but HL is decremented. */
object Cpd : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = mem.read(cpu.hl)
        val r = Flags.afterSub(cpu.a, byte, 0)
        cpu.hl = (cpu.hl - 1) and 0xFFFF
        cpu.bc = (cpu.bc - 1) and 0xFFFF
        var f = (r.newF and (Flags.S or Flags.Z or Flags.H or Flags.N)) or (cpu.f and Flags.C)
        if (cpu.bc != 0) f = f or Flags.PV
        // X/Y per Sean Young's TUZD: with n = (A - byte - H_after), X = bit 1 of n; Y = bit 3 of n.
        val n = (cpu.a - byte - (if (f and Flags.H != 0) 1 else 0)) and 0xFF
        f = f or ((n shl 4) and Flags.X) or (n and Flags.Y)
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "CPD"
}
