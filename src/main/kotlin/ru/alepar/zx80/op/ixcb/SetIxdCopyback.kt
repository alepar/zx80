package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `SET n, (IX+d), r` — undocumented. Set bit n of memory[IX+d], write back to memory AND copy the
 * result to register r. 23 T-states. R+=2. PC+=4. No flag changes.
 *
 * Covers 112 of the 128 SET slots in DDCB+FDCB tables (8 bits × 7 register dsts × 2 prefixes). The
 * remaining 16 slots (rrr=110) are the documented memory-only form handled by SetIxd.
 */
class SetIxdCopyback(private val idx: IndexReg, private val n: Int, private val dst: Reg) : Op {
    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val result = mem.read(addr) or (1 shl n)
        mem.write(addr, result)
        dst.write(cpu, result)
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "SET $n, (${idx.mnemonic}+d), ${dst.mnemonic}"
}
