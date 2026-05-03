package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `BIT n, (IX+d)` / `BIT n, (IY+d)` — test bit n of memory at idx+d. 20 T-states (only reads,
 * doesn't write — distinct from RES/SET (IX+d) which are 23T). R+=2. PC+=4.
 *
 * Flag rules:
 * - Z = !(bit n of byte at idx+d)
 * - H = 1
 * - N = 0
 * - C preserved
 * - S, P/V undocumented — left at 0
 */
class BitIxd(private val idx: IndexReg, private val n: Int) : Op {
    init {
        require(n in 0..7) { "BIT bit number must be 0..7; got $n" }
    }

    override val operandLength = 0
    override val baseCycles = 20

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val bit = (mem.read(addr) shr n) and 1
        var f = cpu.f and Flags.C
        f = f or Flags.H
        if (bit == 0) f = f or Flags.Z
        cpu.f = f
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "BIT $n, (${idx.mnemonic}+d)"
}
