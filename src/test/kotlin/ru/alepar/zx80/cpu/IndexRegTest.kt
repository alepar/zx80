package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IndexRegTest {
    @Test
    fun `IX reads and writes cpu ix`() {
        val cpu = Cpu().apply { ix = 0x1234 }
        assertThat(IndexReg.IX.read(cpu)).isEqualTo(0x1234)
        IndexReg.IX.write(cpu, 0xABCD)
        assertThat(cpu.ix).isEqualTo(0xABCD)
    }

    @Test
    fun `IY reads and writes cpu iy`() {
        val cpu = Cpu().apply { iy = 0x1234 }
        assertThat(IndexReg.IY.read(cpu)).isEqualTo(0x1234)
        IndexReg.IY.write(cpu, 0xABCD)
        assertThat(cpu.iy).isEqualTo(0xABCD)
    }

    @Test
    fun `write masks to 16 bits`() {
        val cpu = Cpu()
        IndexReg.IX.write(cpu, 0x12345)
        assertThat(cpu.ix).isEqualTo(0x2345)
    }

    @Test
    fun `mnemonic`() {
        assertThat(IndexReg.IX.mnemonic).isEqualTo("IX")
        assertThat(IndexReg.IY.mnemonic).isEqualTo("IY")
    }
}
