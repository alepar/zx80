package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `SET n, (HL)` — set bit n of memory[HL]. 15 T-states. R+=2. PC+=2. No flags affected. */
class SetHl(private val n: Int) : Op {
    init {
        require(n in 0..7) { "SET bit number must be 0..7; got $n" }
    }

    override val operandLength = 0
    override val baseCycles = 15

    override fun execute(cpu: Cpu, mem: Memory) {
        val bit = 1 shl n
        mem.write(cpu.hl, mem.read(cpu.hl) or bit)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SET $n, (HL)"
}
