package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `NEG` — A = (0 - A) & 0xFF. ED 44. 8 T-states. R+=2. PC+=2. Flags as if SUB 0 - A: N=1; C set
 * unless oldA was 0; P/V set iff oldA was 0x80; S/Z/H from result.
 */
object Neg : Op {
    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterSub(0, cpu.a, 0)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "NEG"
}
