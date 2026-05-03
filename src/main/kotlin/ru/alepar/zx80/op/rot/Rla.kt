package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RLA` — rotate A left through carry. Old C bit becomes new bit 0; old bit 7 becomes new C. 4
 * T-states. Single opcode 0x17.
 *
 * Flag rules: same as RLCA.
 */
object Rla : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val a = cpu.a
        val oldC = if (cpu.f and Flags.C != 0) 1 else 0
        val newC = (a and 0x80) != 0
        val rotated = ((a shl 1) or oldC) and 0xFF
        val r = Flags.afterRotateA(rotated, newC, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RLA"
}
