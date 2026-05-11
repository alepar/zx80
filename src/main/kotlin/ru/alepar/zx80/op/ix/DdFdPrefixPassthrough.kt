package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * Wraps a main-table Op for a DD/FD prefix slot where the prefix is a no-op: adds the prefix's 4
 * T-states, 1 R, 1 PC, then runs the wrapped op as if the prefix weren't there. Identical net
 * effect to [DdFdNopPrefix] followed by the wrapped op on the next dispatch.
 *
 * Installed by [IxOps.installInto] for every (dd, fd) slot where the main table has a non-null op
 * and the IX/IY-aware version is also absent.
 */
class DdFdPrefixPassthrough(private val wrapped: Op) : Op {
    override val operandLength = 1 + wrapped.operandLength
    override val baseCycles = 4 + wrapped.baseCycles

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.bumpR()
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.tStates += 4
        wrapped.execute(cpu, mem)
    }

    override fun mnemonic(operands: OperandFetcher): String = "DD/FD ${wrapped.mnemonic(operands)}"
}
