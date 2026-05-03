package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdIATest {
    @Test
    fun `LD I, A copies A to I, advances pc by 2, r by 2, 9 T-states, no flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x42
                i = 0
                f = 0xFF
            }
        LdIA.execute(cpu, Memory())
        assertThat(cpu.i).isEqualTo(0x42)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(9L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdIA.mnemonic { 0 }).isEqualTo("LD I, A")
    }

    @Test
    fun `operandLength=0, baseCycles=9`() {
        assertThat(LdIA.operandLength).isZero
        assertThat(LdIA.baseCycles).isEqualTo(9)
    }
}
