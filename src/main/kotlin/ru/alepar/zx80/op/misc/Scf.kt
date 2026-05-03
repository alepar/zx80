package ru.alepar.zx80.op.misc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `SCF` — Set Carry Flag. 4 T-states. Single opcode 0x37. C=1; H=0; N=0; S/Z/PV preserved. A
 * unchanged.
 */
object Scf : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.f = Flags.afterScf(cpu.f)
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SCF"
}
