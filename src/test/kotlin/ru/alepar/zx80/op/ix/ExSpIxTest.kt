package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class ExSpIxTest {
    @Test
    fun `EX (SP), IX swaps idx with top of stack, 23 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0x4000
                ix = 0x1234
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x4000, 0xCD)
                write(0x4001, 0xAB)
            }
        ExSpIx(idx = IndexReg.IX).execute(cpu, mem)
        assertThat(cpu.ix).isEqualTo(0xABCD)
        assertThat(mem.read(0x4000)).isEqualTo(0x34)
        assertThat(mem.read(0x4001)).isEqualTo(0x12)
        assertThat(cpu.sp).isEqualTo(0x4000) // SP unchanged
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `EX (SP), IY works the same on IY`() {
        val cpu =
            Cpu().apply {
                sp = 0x4000
                iy = 0x1234
            }
        val mem =
            Memory().apply {
                write(0x4000, 0xCD)
                write(0x4001, 0xAB)
            }
        ExSpIx(idx = IndexReg.IY).execute(cpu, mem)
        assertThat(cpu.iy).isEqualTo(0xABCD)
        assertThat(mem.read(0x4000)).isEqualTo(0x34)
        assertThat(mem.read(0x4001)).isEqualTo(0x12)
    }

    @Test
    fun `mnemonic`() {
        assertThat(ExSpIx(IndexReg.IX).mnemonic { 0 }).isEqualTo("EX (SP), IX")
        assertThat(ExSpIx(IndexReg.IY).mnemonic { 0 }).isEqualTo("EX (SP), IY")
    }

    @Test
    fun `operandLength=0, baseCycles=23`() {
        val op = ExSpIx(IndexReg.IX)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
