package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `RLCA` — rotate A left circular. Bit 7 → C and → bit 0. 4 T-states. Single opcode 0x07.
 *
 * Flag rules: C from bit 7 of A. H=0, N=0. S, Z, P/V preserved. (NOTE: the CB-prefixed `RLC r`
 * variants compute S/Z/PV from the result; the A-variant does NOT.)
 */
object Rlca : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val a = cpu.a
        val newC = (a and 0x80) != 0
        val rotated = ((a shl 1) or (if (newC) 1 else 0)) and 0xFF
        val r = Flags.afterRotateA(rotated, newC, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RLCA"
}
