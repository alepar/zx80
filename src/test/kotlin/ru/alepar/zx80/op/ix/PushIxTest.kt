package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class PushIxTest {
    @Test
    fun `PUSH IX writes high then low, SP -= 2, 15 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0x4000
                ix = 0x1234
                f = 0xFF
            }
        val mem = Memory()
        PushIx(idx = IndexReg.IX).execute(cpu, mem)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFF)).isEqualTo(0x12)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x34)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(15L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `PUSH IY pushes IY`() {
        val cpu =
            Cpu().apply {
                sp = 0x4000
                iy = 0xABCD
            }
        val mem = Memory()
        PushIx(idx = IndexReg.IY).execute(cpu, mem)
        assertThat(mem.read(0x3FFF)).isEqualTo(0xAB)
        assertThat(mem.read(0x3FFE)).isEqualTo(0xCD)
    }

    @Test
    fun `mnemonic`() {
        assertThat(PushIx(IndexReg.IX).mnemonic { 0 }).isEqualTo("PUSH IX")
        assertThat(PushIx(IndexReg.IY).mnemonic { 0 }).isEqualTo("PUSH IY")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = PushIx(IndexReg.IX)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
