package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `DEC (HL)` — decrement the byte at memory[HL], updating flags. 11 T-states. Single opcode 0x35. N
 * set, C preserved.
 */
object DecHlMem : Op {
    override val operandLength = 0
    override val baseCycles = 11

    override fun execute(cpu: Cpu, mem: Memory) {
        val r = Flags.afterDec(mem.read(cpu.hl), cpu.f)
        mem.write(cpu.hl, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "DEC (HL)"
}
