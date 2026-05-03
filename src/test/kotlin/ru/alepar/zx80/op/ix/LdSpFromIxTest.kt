package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class LdSpFromIxTest {
    @Test
    fun `LD SP, IX copies IX into SP, 10 T-states, pc by 2, r by 2`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0
                ix = 0x1234
                f = 0xFF
            }
        LdSpFromIx(idx = IndexReg.IX).execute(cpu, Memory())
        assertThat(cpu.sp).isEqualTo(0x1234)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(10L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `LD SP, IY copies IY into SP`() {
        val cpu =
            Cpu().apply {
                sp = 0
                iy = 0xABCD
            }
        LdSpFromIx(idx = IndexReg.IY).execute(cpu, Memory())
        assertThat(cpu.sp).isEqualTo(0xABCD)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdSpFromIx(IndexReg.IX).mnemonic { 0 }).isEqualTo("LD SP, IX")
        assertThat(LdSpFromIx(IndexReg.IY).mnemonic { 0 }).isEqualTo("LD SP, IY")
    }

    @Test
    fun `operandLength=0, baseCycles=10`() {
        val op = LdSpFromIx(IndexReg.IX)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
