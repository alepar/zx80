package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `RES n, r` — clear bit n of register r. 8 T-states. R+=2. PC+=2. No flags affected. */
class ResReg(private val n: Int, private val dst: Reg) : Op {
    init {
        require(n in 0..7) { "RES bit number must be 0..7; got $n" }
    }

    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val mask = (1 shl n).inv() and 0xFF
        dst.write(cpu, dst.read(cpu) and mask)
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "RES $n, ${dst.mnemonic}"
}
