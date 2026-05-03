package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class DecIxTest {
    @Test
    fun `DEC IX decrements IX, advances pc by 2, r by 2, 10 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x1234
                f = 0xFF
            }
        DecIx(idx = IndexReg.IX).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x1233)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(10L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `DEC IY wraps from 0x0000 to 0xFFFF`() {
        val cpu = Cpu().apply { iy = 0 }
        DecIx(idx = IndexReg.IY).execute(cpu, Memory())
        assertThat(cpu.iy).isEqualTo(0xFFFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(DecIx(idx = IndexReg.IX).mnemonic { 0 }).isEqualTo("DEC IX")
        assertThat(DecIx(idx = IndexReg.IY).mnemonic { 0 }).isEqualTo("DEC IY")
    }

    @Test
    fun `operandLength=0, baseCycles=10`() {
        val op = DecIx(idx = IndexReg.IX)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
