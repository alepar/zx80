package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.OperandFetcher

/** `OTDR` — block output decrement + repeat. ED BB. Same as Outd with looping until B == 0. */
object Otdr : Op {
    override val operandLength = 0
    override val baseCycles = 16

    override fun execute(cpu: Cpu, mem: Memory) {
        val byte = mem.read(cpu.hl)
        cpu.b = (cpu.b - 1) and 0xFF
        cpu.io.write(cpu.bc, byte)
        cpu.hl = (cpu.hl - 1) and 0xFFFF
        val bAfter = cpu.b
        val temp = byte + cpu.l // L after HL--
        var f = 0
        if (bAfter == 0) f = f or Flags.Z
        if (bAfter and 0x80 != 0) f = f or Flags.S
        if (byte and 0x80 != 0) f = f or Flags.N
        if (temp > 0xFF) f = f or Flags.C or Flags.H
        if (Flags.parity((temp and 0x07) xor bAfter)) f = f or Flags.PV
        f = f or (bAfter and (Flags.X or Flags.Y))
        cpu.f = f
        if (cpu.b != 0) {
            cpu.tStates += 21
        } else {
            cpu.pc = (cpu.pc + 2) and 0xFFFF
            cpu.tStates += 16
        }
        cpu.bumpR(2)
    }

    override fun mnemonic(operands: OperandFetcher) = "OTDR"
}
