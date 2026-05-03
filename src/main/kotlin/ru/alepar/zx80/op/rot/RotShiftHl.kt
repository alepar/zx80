package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `<rotate-shift-op> (HL)` — apply a CB-prefixed rotate/shift to memory[HL]. 15 T-states. R+=2.
 * PC+=2.
 *
 * Covers 7 opcodes (one per RotateOp) at the rrr=110 slot of each row in CB 0x00-0x3F.
 */
class RotShiftHl(private val op: RotateOp) : Op {
    override val operandLength = 0
    override val baseCycles = 15

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = op.apply(mem.read(cpu.hl), cpu.f)
        mem.write(cpu.hl, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} (HL)"
}
