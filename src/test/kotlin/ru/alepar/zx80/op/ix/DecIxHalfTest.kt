package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory

class DecIxHalfTest {
    @Test
    fun `DEC IXH decrements only the high byte of IX`() {
        val cpu = Cpu().apply { ix = 0x12AB }
        DecIxHalf(IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x11AB)
    }

    @Test
    fun `DEC IYL wraps 0x00 to 0xFF and sets S not Z`() {
        val cpu = Cpu().apply { iy = 0xAA00 }
        DecIxHalf(IndexHalfReg.IYL).execute(cpu, Memory())
        assertThat(cpu.iy).isEqualTo(0xAAFF)
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isZero
    }

    @Test
    fun `DEC preserves C flag`() {
        val cpu =
            Cpu().apply {
                ix = 0x0100
                f = Flags.C
            }
        DecIxHalf(IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic and metadata`() {
        val op = DecIxHalf(IndexHalfReg.IYH)
        assertThat(op.mnemonic { 0 }).isEqualTo("DEC IYH")
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
