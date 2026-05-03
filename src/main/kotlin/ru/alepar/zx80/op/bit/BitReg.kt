package ru.alepar.zx80.op.bit

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `BIT n, r` — test bit n of register r. 8 T-states. R+=2. PC+=2.
 *
 * Flag rules: Z = !(r and (1 shl n)); H = 1; N = 0; C preserved; S/PV undocumented (left at 0). The
 * register is NOT modified.
 */
class BitReg(private val n: Int, private val src: Reg) : Op {
    init {
        require(n in 0..7) { "BIT bit number must be 0..7; got $n" }
    }

    override val operandLength = 0
    override val baseCycles = 8

    override fun execute(cpu: Cpu, mem: Memory) {
        val operand = src.read(cpu)
        val bit = (operand shr n) and 1
        var f = cpu.f and Flags.C
        f = f or Flags.H
        if (bit == 0) f = f or (Flags.Z or Flags.PV) // PV mirrors Z
        if (n == 7 && bit != 0) f = f or Flags.S
        f = f or (operand and 0x28) // X/Y from operand
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "BIT $n, ${src.mnemonic}"
}
