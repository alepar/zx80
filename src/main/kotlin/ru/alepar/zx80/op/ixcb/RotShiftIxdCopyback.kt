package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher
import ru.alepar.zx80.op.rot.RotateOp

/**
 * `<rotate-shift-op> (IX+d), r` — undocumented. Rotate or shift the byte at idx+d, write back to
 * memory AND copy the result to register r. 23 T-states. R+=2. PC+=4. Same flag math as the
 * documented memory-only `RotShiftIxd` form (S/Z/PV from result, H=0, N=0, C from shifted-out bit).
 *
 * Covers 112 of the 128 rotate/shift slots in DDCB+FDCB tables (8 ops × 7 register dsts × 2
 * prefixes). The remaining 16 slots (rrr=110) are the documented memory-only form handled by
 * RotShiftIxd.
 */
class RotShiftIxdCopyback(
    private val idx: IndexReg,
    private val op: RotateOp,
    private val dst: Reg,
) : Op {
    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val r = op.apply(mem.read(addr), cpu.f)
        mem.write(addr, r.value)
        dst.write(cpu, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) =
        "${op.mnemonic} (${idx.mnemonic}+d), ${dst.mnemonic}"
}
