package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `SET n, (IX+d)` / `SET n, (IY+d)` — set bit n of memory at idx+d. 23 T-states. R+=2. PC+=4. No
 * flags affected.
 */
class SetIxd(private val idx: IndexReg, private val n: Int) : Op {
    init {
        require(n in 0..7) { "SET bit number must be 0..7; got $n" }
    }

    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val bit = 1 shl n
        mem.write(addr, mem.read(addr) or bit)
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SET $n, (${idx.mnemonic}+d)"
}
