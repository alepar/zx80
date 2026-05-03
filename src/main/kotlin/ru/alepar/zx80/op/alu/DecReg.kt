package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DEC r` — decrement an 8-bit register, updating flags. 4 T-states. 7 opcodes (one per Reg in
 * B/C/D/E/H/L/A): pattern `00 rrr 101` minus rrr=110.
 *
 * The N flag is set; C flag is preserved (unique to INC/DEC).
 */
class DecReg(private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 4

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterDec(dst.read(cpu), cpu.f)
        dst.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DEC ${dst.mnemonic}"
}
