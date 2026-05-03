package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class IncIxTest {
    @Test
    fun `INC IX increments IX, advances pc by 2, r by 2, 10 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x1234
                f = 0xFF
            }
        IncIx(idx = IndexReg.IX).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x1235)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(10L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `INC IY wraps from 0xFFFF to 0x0000`() {
        val cpu = Cpu().apply { iy = 0xFFFF }
        IncIx(idx = IndexReg.IY).execute(cpu, Memory())
        assertThat(cpu.iy).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(IncIx(idx = IndexReg.IX).mnemonic { 0 }).isEqualTo("INC IX")
        assertThat(IncIx(idx = IndexReg.IY).mnemonic { 0 }).isEqualTo("INC IY")
    }

    @Test
    fun `operandLength=0, baseCycles=10`() {
        val op = IncIx(idx = IndexReg.IX)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
