package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DD/FD-as-noop-prefix` — Phase F. Real Z80 hardware behavior: when a DD or FD prefix byte is
 * followed by an opcode that doesn't exist in the DD/FD table (e.g. 0x00 NOP, or another
 * DD/FD/ED/CB prefix), the prefix consumes 4 T-states and bumps R, then re-dispatch picks up the
 * trailing byte as a fresh instruction. This op models that: it advances PC by 1 only (so the next
 * dispatch reads the byte that was at PC+1), bumps R by 1, and adds 4 T-states.
 *
 * The `Dispatcher` returns this op when it would otherwise return `null` for a DD/FD table miss.
 */
object DdFdNopPrefix : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "NOP* (DD/FD prefix)"
}
