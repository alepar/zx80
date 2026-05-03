package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `SET n, r` — set bit n of register r. 8 T-states. R+=2. PC+=2. No flags affected. */
class SetReg(private val n: Int, private val dst: Reg) : Op {
    init {
        require(n in 0..7) { "SET bit number must be 0..7; got $n" }
    }

    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val bit = 1 shl n
        dst.write(cpu, dst.read(cpu) or bit)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SET $n, ${dst.mnemonic}"
}
