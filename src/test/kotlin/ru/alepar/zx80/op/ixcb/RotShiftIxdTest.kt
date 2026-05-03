package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.rot.RotateOp

class RotShiftIxdTest {
    @Test
    fun `RLC (IX+5) rotates byte at IX+5, advances pc by 4, r by 2, 23 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                f = 0
            }
        val mem =
            Memory().apply {
                write(0x100, 0xDD)
                write(0x101, 0xCB)
                write(0x102, 0x05)
                write(0x103, 0x06)
                write(0x4005, 0x80)
            }
        RotShiftIxd(idx = IndexReg.IX, op = RotateOp.RLC).execute(cpu, mem)
        assertThat(mem.read(0x4005)).isEqualTo(0x01)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `SRL (IY-2) handles negative displacement`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0x4000
                f = 0
            }
        val mem =
            Memory().apply {
                write(0x102, 0xFE)
                write(0x3FFE, 0x80)
            }
        RotShiftIxd(idx = IndexReg.IY, op = RotateOp.SRL).execute(cpu, mem)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x40)
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `RR (IX+0) folds in carry`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                ix = 0x4000
                f = Flags.C
            }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x4000, 0x01)
            }
        RotShiftIxd(idx = IndexReg.IX, op = RotateOp.RR).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x80)
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(RotShiftIxd(idx = IndexReg.IX, op = RotateOp.RLC).mnemonic { 0 })
            .isEqualTo("RLC (IX+d)")
        assertThat(RotShiftIxd(idx = IndexReg.IY, op = RotateOp.SRL).mnemonic { 0 })
            .isEqualTo("SRL (IY+d)")
    }

    @Test
    fun `operandLength is 0 and baseCycles is 23`() {
        val op = RotShiftIxd(idx = IndexReg.IX, op = RotateOp.RLC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
