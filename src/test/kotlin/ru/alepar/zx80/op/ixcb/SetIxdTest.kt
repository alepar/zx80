package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class SetIxdTest {
    @Test
    fun `SET 7 (IX+5) sets bit 7 of byte at IX+5, 23 T-states, no flags`() {
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
                write(0x4005, 0x00)
            }
        SetIxd(idx = IndexReg.IX, n = 7).execute(cpu, mem)
        assertThat(mem.read(0x4005)).isEqualTo(0x80)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `SET 0 (IY-1) handles negative displacement`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0x4000
            }
        val mem =
            Memory().apply {
                write(0x102, 0xFF)
                write(0x3FFF, 0xFE)
            }
        SetIxd(idx = IndexReg.IY, n = 0).execute(cpu, mem)
        assertThat(mem.read(0x3FFF)).isEqualTo(0xFF)
    }

    @Test
    fun `SetIxd rejects n outside 0 to 7`() {
        assertThatThrownBy { SetIxd(idx = IndexReg.IX, n = 8) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(SetIxd(idx = IndexReg.IX, n = 0).mnemonic { 0 }).isEqualTo("SET 0, (IX+d)")
        assertThat(SetIxd(idx = IndexReg.IY, n = 7).mnemonic { 0 }).isEqualTo("SET 7, (IY+d)")
    }

    @Test
    fun `operandLength is 0 and baseCycles is 23`() {
        val op = SetIxd(idx = IndexReg.IX, n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
