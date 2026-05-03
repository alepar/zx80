package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RRCA` — rotate A right circular. Bit 0 → C and → bit 7. 4 T-states. Single opcode 0x0F.
 *
 * Flag rules: same as RLCA — C from bit 0 of A; H/N=0; S/Z/PV preserved.
 */
object Rrca : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val a = cpu.a
        val newC = (a and 0x01) != 0
        val rotated = ((a ushr 1) or (if (newC) 0x80 else 0)) and 0xFF
        val r = Flags.afterRotateA(rotated, newC, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RRCA"
}
