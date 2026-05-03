package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD R, A` — copy A into the memory refresh register R. ED 4F. 9 T-states. PC+=2. R is then ALSO
 * incremented by the normal bumpR(2) path. No flag changes.
 */
object LdRA : Op {
    override val operandLength = 0
    override val baseCycles = 9

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.r = cpu.a
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD R, A"
}
