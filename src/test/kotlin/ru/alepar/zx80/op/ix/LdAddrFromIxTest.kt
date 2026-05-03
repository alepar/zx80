package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class LdAddrFromIxTest {
    @Test
    fun `LD (nn), IX writes IX as little-endian, 20 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x1234
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x103, 0x40)
            }
        LdAddrFromIx(idx = IndexReg.IX).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x34)
        assertThat(mem.read(0x4001)).isEqualTo(0x12)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(20L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `LD (nn), IY writes IY`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0xABCD
            }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x103, 0x40)
            }
        LdAddrFromIx(idx = IndexReg.IY).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0xCD)
        assertThat(mem.read(0x4001)).isEqualTo(0xAB)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAddrFromIx(idx = IndexReg.IX).mnemonic { 0 }).isEqualTo("LD (nn), IX")
        assertThat(LdAddrFromIx(idx = IndexReg.IY).mnemonic { 0 }).isEqualTo("LD (nn), IY")
    }

    @Test
    fun `operandLength=2, baseCycles=20`() {
        val op = LdAddrFromIx(idx = IndexReg.IX)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(20)
    }
}
