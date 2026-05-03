package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DJNZ e` — Decrement B; jump relative if B != 0. The decrement does NOT update flags (despite
 * being a decrement). 8 T-states if B=0 after; 13 T-states if jumped. Single opcode 0x10.
 */
object Djnz : Op {
    override val operandLength = 1
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.b = (cpu.b - 1) and 0xFF
        val e = mem.read(cpu.pc + 1).toByte().toInt()
        if (cpu.b != 0) {
            cpu.pc = (cpu.pc + 2 + e) and 0xFFFF
            cpu.tStates += 5 // extra cycles for taken branch
        } else {
            cpu.pc = (cpu.pc + 2) and 0xFFFF
        }
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DJNZ e"
}
