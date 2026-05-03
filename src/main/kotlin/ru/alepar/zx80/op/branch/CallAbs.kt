package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.push
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `CALL nn` — push the return address (pc+3) onto the stack and jump to nn. 17 T-states. No flag
 * changes.
 */
object CallAbs : Op {
    override val operandLength = 2
    override val baseCycles = 17

    override fun execute(cpu: Cpu, mem: Memory) {
        val nn = mem.readWord(cpu.pc + 1)
        val returnAddr = (cpu.pc + 3) and 0xFFFF
        cpu.push(mem, returnAddr)
        cpu.pc = nn
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "CALL nn"
}
