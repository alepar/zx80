package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `IN A, (n)` — read I/O port into A. 0xDB nn (main table). 11 T-states. R+=1. PC+=2.
 *
 * Port = (cpu.a shl 8) or n — high byte from A, low byte from immediate. No flag changes.
 */
object InAImm : Op {
    override val operandLength = 1
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read(cpu.pc + 1)
        val port = (cpu.a shl 8) or n
        cpu.a = cpu.io.read(port) and 0xFF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "IN A, (n)"
}
