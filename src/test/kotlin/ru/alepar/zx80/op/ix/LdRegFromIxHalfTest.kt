package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegFromIxHalfTest {
    @Test
    fun `LD B, IXH copies high byte of IX into B`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x1234
                b = 0
            }
        LdRegFromIxHalf(dst = Reg.B, src = IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x12)
        assertThat(cpu.ix).isEqualTo(0x1234)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `LD A, IYL copies low byte of IY into A`() {
        val cpu = Cpu().apply { iy = 0xAABB }
        LdRegFromIxHalf(dst = Reg.A, src = IndexHalfReg.IYL).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xBB)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdRegFromIxHalf(Reg.B, IndexHalfReg.IXH).mnemonic { 0 }).isEqualTo("LD B, IXH")
        assertThat(LdRegFromIxHalf(Reg.A, IndexHalfReg.IYL).mnemonic { 0 }).isEqualTo("LD A, IYL")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = LdRegFromIxHalf(Reg.B, IndexHalfReg.IXH)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
