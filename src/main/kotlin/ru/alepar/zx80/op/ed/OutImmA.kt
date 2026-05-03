package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `OUT (n), A` — write A to I/O port. 0xD3 nn (main table). 11 T-states. R+=1. PC+=2.
 *
 * Port = (cpu.a shl 8) or n. No flag changes.
 */
object OutImmA : Op {
    override val operandLength = 1
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read(cpu.pc + 1)
        val port = (cpu.a shl 8) or n
        cpu.io.write(port, cpu.a)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "OUT (n), A"
}
