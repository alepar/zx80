package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

object Halt : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.halted = true
        cpu.bumpR()
        cpu.tStates += baseCycles
        // Phase F: Z80 hardware leaves PC at the HALT byte during halted state. The dispatcher's
        // !cpu.halted guard prevents re-execution; on INT acknowledge, the interrupt mechanism is
        // responsible for advancing PC past the HALT.
    }

    override fun mnemonic(operands: OperandFetcher) = "HALT"
}
