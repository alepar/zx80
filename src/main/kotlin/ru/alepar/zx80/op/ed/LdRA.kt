package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD R, A` — copy A into the memory refresh register R. ED 4F. 9 T-states. PC+=2.
 *
 * Per the real Z80, the two M1 cycles (ED prefix, 0x4F opcode) tick R *before* the load executes,
 * so R ends up with A's value (not A + 2). We perform `bumpR(2)` first, then `R = A`. No flag
 * changes.
 */
object LdRA : Op {
    override val operandLength = 0
    override val baseCycles = 9

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.r = cpu.a
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD R, A"
}
