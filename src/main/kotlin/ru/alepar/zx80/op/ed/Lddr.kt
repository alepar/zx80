package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/**
 * `LDDR` — block move decrement + repeat. ED B8. Same as Ldd, but loops until BC == 0 by leaving PC
 * unchanged when BC != 0 (21 T-states per loop iteration; 16 on exit).
 */
object Lddr : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = mem.read(cpu.hl)
        mem.write(cpu.de, byte)
        cpu.hl = (cpu.hl - 1) and 0xFFFF
        cpu.de = (cpu.de - 1) and 0xFFFF
        cpu.bc = (cpu.bc - 1) and 0xFFFF
        var f = cpu.f and (Flags.S or Flags.Z or Flags.C)
        if (cpu.bc != 0) f = f or Flags.PV
        // X/Y per Sean Young's TUZD (see Ldi): n = byte + A; X = bit 1 of n; Y = bit 3 of n.
        val n = (byte + cpu.a) and 0xFF
        f = f or ((n shl 4) and Flags.X) or (n and Flags.Y)
        cpu.f = f
        if (cpu.bc != 0) {
            cpu.tStates += 21
        } else {
            cpu.pc = (cpu.pc + 2) and 0xFFFF
            cpu.tStates += 16
        }
        cpu.bumpR(2)
    }

    override fun mnemonic(operands: OperandFetcher) = "LDDR"
}
