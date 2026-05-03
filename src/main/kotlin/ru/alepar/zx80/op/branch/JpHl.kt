package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `JP (HL)` — jump to the value of HL. Despite the parens, this is NOT a memory dereference — pc is
 * set to HL itself. 4 T-states. Single opcode 0xE9. No flag changes.
 */
object JpHl : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = cpu.hl
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "JP (HL)"
}
