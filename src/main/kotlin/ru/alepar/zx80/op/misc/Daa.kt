package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DAA` — BCD adjust accumulator. Adjusts A based on N, H, C from a previous arithmetic operation
 * so the result is correct in BCD. 4 T-states. Single opcode 0x27. Flag rules: see Flags.afterDaa.
 */
object Daa : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterDaa(cpu.a, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DAA"
}
