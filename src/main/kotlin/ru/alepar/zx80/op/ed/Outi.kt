package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `OUTI` — block output increment. ED A3. 16 T-states. R+=2. PC+=2.
 *
 * On real Z80: B is decremented BEFORE the port write so the port (which uses BC as address) sees
 * the new B. We follow that ordering. Then mem[HL] is written to port; HL++.
 */
object Outi : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = mem.read(cpu.hl)
        val oldL = cpu.l
        cpu.b = (cpu.b - 1) and 0xFF
        cpu.io.write(cpu.bc, byte)
        cpu.hl = (cpu.hl + 1) and 0xFFFF
        var f = Flags.N
        if (cpu.b == 0) f = f or Flags.Z
        // X/Y from n = (byte + L) and 0xFF per Zilog NMOS (L before HL update)
        val n = (byte + oldL) and 0xFF
        f = f or (n and 0x28)
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) = "OUTI"
}
