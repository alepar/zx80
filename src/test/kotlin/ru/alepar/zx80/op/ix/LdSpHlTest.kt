package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdSpHlTest {
    @Test
    fun `LD SP, HL copies HL into SP, advances pc, increments r, adds 6 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x1234
                sp = 0
                f = 0xFF
            }
        LdSpHl.execute(cpu, Memory())
        assertThat(cpu.sp).isEqualTo(0x1234)
        assertThat(cpu.hl).isEqualTo(0x1234)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(6L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdSpHl.mnemonic { 0 }).isEqualTo("LD SP, HL")
    }

    @Test
    fun `operandLength=0, baseCycles=6`() {
        assertThat(LdSpHl.operandLength).isZero
        assertThat(LdSpHl.baseCycles).isEqualTo(6)
    }
}
