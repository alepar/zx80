package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD r, (HL)` — copies the byte at memory[HL] into register r. 7 T-states. No flag changes. HL is
 * not modified.
 */
class LdRegFromHl(private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 7

    override fun execute(cpu: Cpu, mem: Memory) {
        dst.write(cpu, mem.read(cpu.hl))
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, (HL)"
}
