package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory

class LdIxHalfFromIxHalfTest {
    @Test
    fun `LD IXH, IXL copies low byte to high byte of IX`() {
        val cpu = Cpu().apply { ix = 0x1234 }
        LdIxHalfFromIxHalf(dst = IndexHalfReg.IXH, src = IndexHalfReg.IXL).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x3434)
    }

    @Test
    fun `LD IYL, IYH copies high byte to low byte of IY`() {
        val cpu = Cpu().apply { iy = 0xABCD }
        LdIxHalfFromIxHalf(dst = IndexHalfReg.IYL, src = IndexHalfReg.IYH).execute(cpu, Memory())
        assertThat(cpu.iy).isEqualTo(0xABAB)
    }

    @Test
    fun `LD IXH, IXH is a no-op on IX value`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                ix = 0x1234
            }
        LdIxHalfFromIxHalf(IndexHalfReg.IXH, IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x1234)
        assertThat(cpu.pc).isEqualTo(0x102)
    }

    @Test
    fun `mnemonic and metadata`() {
        val op = LdIxHalfFromIxHalf(IndexHalfReg.IXH, IndexHalfReg.IXL)
        assertThat(op.mnemonic { 0 }).isEqualTo("LD IXH, IXL")
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
