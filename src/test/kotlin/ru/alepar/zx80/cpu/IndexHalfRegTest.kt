package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IndexHalfRegTest {
    @Test
    fun `IXH reads high byte of cpu ix and writes only the high byte`() {
        val cpu = Cpu().apply { ix = 0xABCD }
        assertThat(IndexHalfReg.IXH.read(cpu)).isEqualTo(0xAB)

        IndexHalfReg.IXH.write(cpu, 0x12)
        assertThat(cpu.ix).isEqualTo(0x12CD)
    }

    @Test
    fun `IXL reads low byte of cpu ix and writes only the low byte`() {
        val cpu = Cpu().apply { ix = 0xABCD }
        assertThat(IndexHalfReg.IXL.read(cpu)).isEqualTo(0xCD)

        IndexHalfReg.IXL.write(cpu, 0x12)
        assertThat(cpu.ix).isEqualTo(0xAB12)
    }

    @Test
    fun `IYH reads high byte of cpu iy`() {
        val cpu = Cpu().apply { iy = 0xABCD }
        assertThat(IndexHalfReg.IYH.read(cpu)).isEqualTo(0xAB)

        IndexHalfReg.IYH.write(cpu, 0x12)
        assertThat(cpu.iy).isEqualTo(0x12CD)
    }

    @Test
    fun `IYL reads low byte of cpu iy`() {
        val cpu = Cpu().apply { iy = 0xABCD }
        assertThat(IndexHalfReg.IYL.read(cpu)).isEqualTo(0xCD)

        IndexHalfReg.IYL.write(cpu, 0x12)
        assertThat(cpu.iy).isEqualTo(0xAB12)
    }

    @Test
    fun `write masks to 8 bits`() {
        val cpu = Cpu().apply { ix = 0 }
        IndexHalfReg.IXH.write(cpu, 0x1FF)
        assertThat(cpu.ix).isEqualTo(0xFF00)

        IndexHalfReg.IXL.write(cpu, -1)
        assertThat(cpu.ix).isEqualTo(0xFFFF)
    }

    @Test
    fun `mnemonic and parent and isHigh`() {
        assertThat(IndexHalfReg.IXH.mnemonic).isEqualTo("IXH")
        assertThat(IndexHalfReg.IXH.parent).isEqualTo(IndexReg.IX)
        assertThat(IndexHalfReg.IXH.isHigh).isTrue

        assertThat(IndexHalfReg.IXL.mnemonic).isEqualTo("IXL")
        assertThat(IndexHalfReg.IXL.parent).isEqualTo(IndexReg.IX)
        assertThat(IndexHalfReg.IXL.isHigh).isFalse

        assertThat(IndexHalfReg.IYH.parent).isEqualTo(IndexReg.IY)
        assertThat(IndexHalfReg.IYL.parent).isEqualTo(IndexReg.IY)
    }
}
