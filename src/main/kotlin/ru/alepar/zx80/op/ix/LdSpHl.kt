package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD SP, HL` — copy HL into SP. 6 T-states. Single opcode 0xF9 in the main table. No flag changes.
 * The straggler from Phase 2.5 that lands here alongside its IX/IY siblings.
 */
object LdSpHl : Op {
    override val operandLength = 0
    override val baseCycles = 6

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.sp = cpu.hl
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD SP, HL"
}
