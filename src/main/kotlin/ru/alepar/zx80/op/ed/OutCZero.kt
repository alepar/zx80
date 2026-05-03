package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `OUT (C), 0` — write 0 to I/O port BC. ED 71 (rrr=110 case). 12 T-states. R+=2. PC+=2. No flag
 * changes. (Some Z80 variants write 0xFF instead; we go with 0 — the standard NMOS behavior.)
 */
object OutCZero : Op {
    override val operandLength = 0
    override val baseCycles = 12

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.io.write(cpu.bc, 0)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "OUT (C), 0"
}
