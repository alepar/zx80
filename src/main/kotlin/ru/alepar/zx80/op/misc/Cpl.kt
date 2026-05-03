package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `CPL` — A = A xor 0xFF. 4 T-states. Single opcode 0x2F. H=1, N=1; S/Z/PV/C preserved. */
object Cpl : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterCpl(cpu.a, cpu.f)
        cpu.a = r.value
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "CPL"
}
