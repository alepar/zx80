package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdIxHalfFromRegTest {
    @Test
    fun `LD IXH, B writes B into the high byte of IX leaving low byte intact`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                ix = 0xAACC
                b = 0x42
            }
        LdIxHalfFromReg(dst = IndexHalfReg.IXH, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x42CC)
        assertThat(cpu.pc).isEqualTo(0x102)
    }

    @Test
    fun `LD IYL, A writes A into low byte of IY`() {
        val cpu =
            Cpu().apply {
                iy = 0xAA00
                a = 0x99
            }
        LdIxHalfFromReg(dst = IndexHalfReg.IYL, src = Reg.A).execute(cpu, Memory())
        assertThat(cpu.iy).isEqualTo(0xAA99)
    }

    @Test
    fun `mnemonic and metadata`() {
        val op = LdIxHalfFromReg(IndexHalfReg.IXH, Reg.B)
        assertThat(op.mnemonic { 0 }).isEqualTo("LD IXH, B")
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
