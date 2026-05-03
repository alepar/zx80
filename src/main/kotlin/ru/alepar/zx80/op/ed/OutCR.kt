package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `OUT (C), r` — write register r to I/O port BC. ED 41+8*r (r != 6). 12 T-states. R+=2. PC+=2. No
 * flag changes.
 */
class OutCR(private val src: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 12

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.io.write(cpu.bc, src.read(cpu) and 0xFF)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "OUT (C), ${src.mnemonic}"
}
