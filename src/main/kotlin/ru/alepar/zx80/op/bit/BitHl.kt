package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `BIT n, (HL)` — test bit n of memory[HL]. 12 T-states (only reads, doesn't write — distinct from
 * SET/RES (HL) which are 15T). R+=2. PC+=2. Memory unchanged. Flag rules same as BitReg.
 */
class BitHl(private val n: Int) : Op {
    init {
        require(n in 0..7) { "BIT bit number must be 0..7; got $n" }
    }

    override val operandLength = 0
    override val baseCycles = 12

    override fun execute(cpu: Cpu, mem: Memory) {
        val bit = (mem.read(cpu.hl) shr n) and 1
        var f = cpu.f and Flags.C
        f = f or Flags.H
        if (bit == 0) f = f or Flags.Z
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "BIT $n, (HL)"
}
