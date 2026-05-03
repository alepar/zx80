package ru.alepar.zx80.op.ixcb

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher
import ru.alepar.zx80.op.rot.RotateOp

/**
 * `<rotate-shift-op> (IX+d)` / `<rotate-shift-op> (IY+d)` — apply a CB-style rotate/shift to the
 * byte at idx+d. 23 T-states. R+=2. PC+=4.
 *
 * Displacement is signed 8-bit at pc+2; opcode at pc+3. Effective address wraps mod 64K. Reuses
 * RotateOp.apply for the per-op bit manipulation + flag computation (S/Z/PV from result, H=0, N=0,
 * C from shifted-out bit).
 */
class RotShiftIxd(private val idx: IndexReg, private val op: RotateOp) : Op {
    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val r = op.apply(mem.read(addr), cpu.f)
        mem.write(addr, r.value)
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "${op.mnemonic} (${idx.mnemonic}+d)"
}
