package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory

class IncIxHalfTest {
    @Test
    fun `INC IXH increments only the high byte of IX`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x10AB
            }
        IncIxHalf(IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x11AB)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `INC IYL wraps low byte 0xFF to 0x00 and sets Z`() {
        val cpu = Cpu().apply { iy = 0xAA_FF }
        IncIxHalf(IndexHalfReg.IYL).execute(cpu, Memory())
        assertThat(cpu.iy).isEqualTo(0xAA00)
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `INC preserves C flag`() {
        val cpu =
            Cpu().apply {
                ix = 0x0000
                f = Flags.C
            }
        IncIxHalf(IndexHalfReg.IXL).execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(IncIxHalf(IndexHalfReg.IXH).mnemonic { 0 }).isEqualTo("INC IXH")
        assertThat(IncIxHalf(IndexHalfReg.IYL).mnemonic { 0 }).isEqualTo("INC IYL")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = IncIxHalf(IndexHalfReg.IXH)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
