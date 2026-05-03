package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class ResIxdTest {
    @Test
    fun `RES 7 (IX+5) clears bit 7 of byte at IX+5, advances pc by 4, r by 2, 23 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x102, 0x05)
                write(0x4005, 0xFF)
            }
        ResIxd(idx = IndexReg.IX, n = 7).execute(cpu, mem)
        assertThat(mem.read(0x4005)).isEqualTo(0x7F)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `RES 0 (IY-2) handles negative displacement`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0x4000
            }
        val mem =
            Memory().apply {
                write(0x102, 0xFE)
                write(0x3FFE, 0xFF)
            }
        ResIxd(idx = IndexReg.IY, n = 0).execute(cpu, mem)
        assertThat(mem.read(0x3FFE)).isEqualTo(0xFE)
    }

    @Test
    fun `ResIxd rejects n outside 0 to 7`() {
        assertThatThrownBy { ResIxd(idx = IndexReg.IX, n = 8) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(ResIxd(idx = IndexReg.IX, n = 0).mnemonic { 0 }).isEqualTo("RES 0, (IX+d)")
        assertThat(ResIxd(idx = IndexReg.IY, n = 7).mnemonic { 0 }).isEqualTo("RES 7, (IY+d)")
    }

    @Test
    fun `operandLength is 0 and baseCycles is 23`() {
        val op = ResIxd(idx = IndexReg.IX, n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
