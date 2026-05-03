package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class BitIxdTest {
    @Test
    fun `BIT 7 (IX+5) tests bit 7 of byte at IX+5, advances pc by 4, r by 2, 20 T-states`() {
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
                write(0x102, 0x05)
                write(0x4005, 0x80)
            }
        BitIxd(idx = IndexReg.IX, n = 7).execute(cpu, mem)
        assertThat(cpu.f and Flags.Z).isZero
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.f and Flags.N).isZero
        assertThat(mem.read(0x4005)).isEqualTo(0x80)
        assertThat(cpu.ix).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(20L)
    }

    @Test
    fun `BIT 0 (IY-1) sets Z when bit is clear`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0x4000
                f = 0
            }
        val mem =
            Memory().apply {
                write(0x102, 0xFF)
                write(0x3FFF, 0xFE)
            }
        BitIxd(idx = IndexReg.IY, n = 0).execute(cpu, mem)
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `BIT preserves C flag`() {
        val cpu =
            Cpu().apply {
                ix = 0x4000
                f = Flags.C
            }
        val mem =
            Memory().apply {
                write(0x102, 0)
                write(0x4000, 0x80)
            }
        BitIxd(idx = IndexReg.IX, n = 7).execute(cpu, mem)
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `BitIxd rejects n outside 0 to 7`() {
        assertThatThrownBy { BitIxd(idx = IndexReg.IX, n = 8) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { BitIxd(idx = IndexReg.IX, n = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(BitIxd(idx = IndexReg.IX, n = 0).mnemonic { 0 }).isEqualTo("BIT 0, (IX+d)")
        assertThat(BitIxd(idx = IndexReg.IY, n = 7).mnemonic { 0 }).isEqualTo("BIT 7, (IY+d)")
    }

    @Test
    fun `operandLength is 0 and baseCycles is 20`() {
        val op = BitIxd(idx = IndexReg.IX, n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(20)
    }

    @Test
    fun `BIT n (IX+d) sets cpu memptr to IX plus d and X-Y from memptr high byte`() {
        val cpu =
            Cpu().apply {
                ix = 0x2010
                memptr = 0
            }
        val mem =
            Memory().apply {
                write(0x102, 0x18)
                write(0x2010 + 0x18, 0xFF)
            }
        cpu.pc = 0x100
        BitIxd(IndexReg.IX, 0).execute(cpu, mem)
        assertThat(cpu.memptr).isEqualTo(0x2028)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isZero
    }

    @Test
    fun `BIT n (IY+d) handles negative displacement and updates memptr`() {
        val cpu = Cpu().apply { iy = 0x2828 }
        val mem =
            Memory().apply {
                write(0x102, 0xF0)
                write(0x2828 - 16, 0xFF)
            }
        cpu.pc = 0x100
        BitIxd(IndexReg.IY, 0).execute(cpu, mem)
        assertThat(cpu.memptr).isEqualTo(0x2818)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isNotZero
    }
}
