package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `OTIR` — block output increment + repeat. ED B3. Same as Outi with looping until B == 0. */
object Otir : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = mem.read(cpu.hl)
        cpu.b = (cpu.b - 1) and 0xFF
        cpu.io.write(cpu.bc, byte)
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        var f = Flags.N
        if (cpu.b == 0) f = f or Flags.Z
        cpu.f = f
        if (cpu.b != 0) {
            cpu.tStates += 21
        } else {
            cpu.pc = (cpu.pc + 2) and 0xFFFF
            cpu.tStates += 16
        }
        cpu.bumpR(2)
    }

    override fun mnemonic(operands: OperandFetcher) = "OTIR"
}
