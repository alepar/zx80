package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LD r, (IX+d)` / `LD r, (IY+d)` — load byte at idx+d into register r. 19 T-states. R+=2. PC+=3
 * (prefix + opcode + displacement). No flags.
 *
 * Displacement is signed 8-bit; effective address wraps mod 64K.
 */
class LdRegFromIxd(private val idx: IndexReg, private val dst: Reg) : Op {
    override val operandLength = 1
    override val baseCycles = 19

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        dst.write(cpu, mem.read(addr))
        cpu.pc = (cpu.pc + 3) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "LD ${dst.mnemonic}, (${idx.mnemonic}+d)"
}
