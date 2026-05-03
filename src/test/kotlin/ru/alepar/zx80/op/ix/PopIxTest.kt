package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class PopIxTest {
    @Test
    fun `POP IX reads low then high, SP += 2, 14 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0x3FFE
                ix = 0
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x3FFE, 0x34)
                write(0x3FFF, 0x12)
            }
        PopIx(idx = IndexReg.IX).execute(cpu, mem)
        assertThat(cpu.ix).isEqualTo(0x1234)
        assertThat(cpu.sp).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(14L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `POP IY writes IY`() {
        val cpu =
            Cpu().apply {
                sp = 0x3FFE
                iy = 0
            }
        val mem =
            Memory().apply {
                write(0x3FFE, 0xCD)
                write(0x3FFF, 0xAB)
            }
        PopIx(idx = IndexReg.IY).execute(cpu, mem)
        assertThat(cpu.iy).isEqualTo(0xABCD)
    }

    @Test
    fun `mnemonic`() {
        assertThat(PopIx(IndexReg.IX).mnemonic { 0 }).isEqualTo("POP IX")
        assertThat(PopIx(IndexReg.IY).mnemonic { 0 }).isEqualTo("POP IY")
    }

    @Test
    fun `operandLength=0, baseCycles=14`() {
        val op = PopIx(IndexReg.IX)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(14)
    }
}
