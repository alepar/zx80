package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class SetIxdCopybackTest {
    @Test
    fun `SET 0, (IX+1), B sets bit 0 of memory and copies to B`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                b = 0
                f = 0x55
            }
        val mem =
            Memory().apply {
                write(0x102, 0x01)
                write(0x4001, 0x00)
            }
        SetIxdCopyback(IndexReg.IX, n = 0, dst = Reg.B).execute(cpu, mem)
        assertThat(mem.read(0x4001)).isEqualTo(0x01)
        assertThat(cpu.b).isEqualTo(0x01)
        assertThat(cpu.f).isEqualTo(0x55)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `SET 7, (IY+0), A sets high bit of memory and copies to A`() {
        val cpu = Cpu().apply { iy = 0x4000 }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x4000, 0x00)
            }
        cpu.pc = 0x100
        SetIxdCopyback(IndexReg.IY, n = 7, dst = Reg.A).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x80)
        assertThat(cpu.a).isEqualTo(0x80)
    }

    @Test
    fun `SET preserves already-set bits`() {
        val cpu = Cpu().apply { ix = 0x4000 }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x4000, 0x55)
            }
        cpu.pc = 0x100
        SetIxdCopyback(IndexReg.IX, n = 1, dst = Reg.C).execute(cpu, mem)
        // 0x55 = 01010101, set bit 1 -> 01010111 = 0x57
        assertThat(mem.read(0x4000)).isEqualTo(0x57)
        assertThat(cpu.c).isEqualTo(0x57)
    }

    @Test
    fun `mnemonic`() {
        assertThat(SetIxdCopyback(IndexReg.IX, 3, Reg.B).mnemonic { 0 })
            .isEqualTo("SET 3, (IX+d), B")
        assertThat(SetIxdCopyback(IndexReg.IY, 0, Reg.A).mnemonic { 0 })
            .isEqualTo("SET 0, (IY+d), A")
    }

    @Test
    fun `operandLength=0, baseCycles=23`() {
        val op = SetIxdCopyback(IndexReg.IX, 0, Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
