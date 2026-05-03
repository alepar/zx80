package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class JpIxTest {
    @Test
    fun `JP (IX) sets pc to IX value (NOT memory at IX), 8 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
            }
        val mem =
            Memory().apply {
                write(0x4000, 0x99)
                write(0x4001, 0x99)
            }
        JpIx(idx = IndexReg.IX).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x4000)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `JP (IY) sets pc to IY value`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0x8000
            }
        JpIx(idx = IndexReg.IY).execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x8000)
    }

    @Test
    fun `JP (IX) does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                ix = 0x4000
                f = 0xFF
            }
        JpIx(idx = IndexReg.IX).execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JpIx(idx = IndexReg.IX).mnemonic { 0 }).isEqualTo("JP (IX)")
        assertThat(JpIx(idx = IndexReg.IY).mnemonic { 0 }).isEqualTo("JP (IY)")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = JpIx(idx = IndexReg.IX)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
