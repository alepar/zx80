package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class ResIxdCopybackTest {
    @Test
    fun `RES 0, (IX+1), B clears bit 0 of memory and copies to B`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                b = 0xFF
                f = 0x55 // verify flags untouched
            }
        val mem =
            Memory().apply {
                write(0x102, 0x01)
                write(0x4001, 0x0F)
            }
        ResIxdCopyback(IndexReg.IX, n = 0, dst = Reg.B).execute(cpu, mem)
        // 0x0F RES 0 -> 0x0E
        assertThat(mem.read(0x4001)).isEqualTo(0x0E)
        assertThat(cpu.b).isEqualTo(0x0E)
        assertThat(cpu.f).isEqualTo(0x55) // no flag changes
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `RES 7, (IY+0), A clears high bit of memory and copies to A`() {
        val cpu = Cpu().apply { iy = 0x4000 }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x4000, 0xFF)
            }
        cpu.pc = 0x100
        ResIxdCopyback(IndexReg.IY, n = 7, dst = Reg.A).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x7F)
        assertThat(cpu.a).isEqualTo(0x7F)
    }

    @Test
    fun `signed negative displacement wraps`() {
        val cpu =
            Cpu().apply {
                pc = 0x200
                ix = 0x10
            }
        val mem =
            Memory().apply {
                write(0x202, 0xF0) // d = -16
                write(0x00, 0xFF)
            }
        ResIxdCopyback(IndexReg.IX, n = 3, dst = Reg.C).execute(cpu, mem)
        // 0xFF RES 3 -> 0xF7
        assertThat(mem.read(0x00)).isEqualTo(0xF7)
        assertThat(cpu.c).isEqualTo(0xF7)
    }

    @Test
    fun `mnemonic`() {
        assertThat(ResIxdCopyback(IndexReg.IX, 3, Reg.B).mnemonic { 0 })
            .isEqualTo("RES 3, (IX+d), B")
        assertThat(ResIxdCopyback(IndexReg.IY, 7, Reg.A).mnemonic { 0 })
            .isEqualTo("RES 7, (IY+d), A")
    }

    @Test
    fun `operandLength=0, baseCycles=23`() {
        val op = ResIxdCopyback(IndexReg.IX, 0, Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
