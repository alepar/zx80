package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `INIR` — block input increment + repeat. ED B2. Same as Ini but loops while B != 0. Loop
 * iteration: 21 T-states (PC unchanged). Exit (B == 0): 16 T-states (PC += 2).
 */
object Inir : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = cpu.io.read(cpu.bc) and 0xFF
        mem.write(cpu.hl, byte)
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        cpu.b = (cpu.b - 1) and 0xFF
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

    override fun mnemonic(operands: OperandFetcher) = "INIR"
}
