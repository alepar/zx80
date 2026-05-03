package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.rot.RotateOp

class RotShiftIxdCopybackTest {
    @Test
    fun `RLC (IX+1), B rotates memory and copies result to B`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                b = 0
            }
        // displacement at pc+2 = 0x102
        val mem =
            Memory().apply {
                write(0x102, 0x01) // d = +1
                write(0x4001, 0x80) // value to rotate
            }
        RotShiftIxdCopyback(IndexReg.IX, RotateOp.RLC, Reg.B).execute(cpu, mem)
        // 0x80 RLC -> 0x01, C=1
        assertThat(mem.read(0x4001)).isEqualTo(0x01)
        assertThat(cpu.b).isEqualTo(0x01) // copy-back
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `signed negative displacement wraps`() {
        val cpu =
            Cpu().apply {
                pc = 0x200
                ix = 0x10
            }
        // d = -16 (0xF0), addr = 0x10 + (-16) = 0x00
        val mem =
            Memory().apply {
                write(0x202, 0xF0)
                write(0x00, 0x55)
            }
        RotShiftIxdCopyback(IndexReg.IX, RotateOp.RLC, Reg.A).execute(cpu, mem)
        // 0x55 RLC -> 0xAA
        assertThat(mem.read(0x00)).isEqualTo(0xAA)
        assertThat(cpu.a).isEqualTo(0xAA)
    }

    @Test
    fun `SLL (IY+0), C copies result to C and shifts left, sets bit 0`() {
        val cpu = Cpu().apply { iy = 0x4000 }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x4000, 0x55)
            }
        cpu.pc = 0x100
        RotShiftIxdCopyback(IndexReg.IY, RotateOp.SLL, Reg.C).execute(cpu, mem)
        // 0x55 SLL -> shift left to 0xAA, set bit 0 -> 0xAB
        assertThat(mem.read(0x4000)).isEqualTo(0xAB)
        assertThat(cpu.c).isEqualTo(0xAB)
    }

    @Test
    fun `mnemonic format is OP (idx+d), dst`() {
        assertThat(RotShiftIxdCopyback(IndexReg.IX, RotateOp.RLC, Reg.B).mnemonic { 0 })
            .isEqualTo("RLC (IX+d), B")
        assertThat(RotShiftIxdCopyback(IndexReg.IY, RotateOp.SRL, Reg.A).mnemonic { 0 })
            .isEqualTo("SRL (IY+d), A")
    }

    @Test
    fun `operandLength=0, baseCycles=23`() {
        val op = RotShiftIxdCopyback(IndexReg.IX, RotateOp.RLC, Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
