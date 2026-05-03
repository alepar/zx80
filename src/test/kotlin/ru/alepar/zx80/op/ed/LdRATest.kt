package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdRATest {
    @Test
    fun `LD R, A copies all 8 bits of A into R then bumpR(2)`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0x55
                tStates = 0L
                a = 0xAB
            }
        LdRA.execute(cpu, Memory())
        // R = 0xAB then bumpR(2): top bit (0x80) preserved; low 7 = (0x2B + 2) & 0x7F = 0x2D.
        // Final: 0x80 | 0x2D = 0xAD.
        assertThat(cpu.r).isEqualTo(0xAD)
    }

    @Test
    fun `LD R, A — A unchanged, no flags, 9 T-states`() {
        val cpu =
            Cpu().apply {
                a = 0x42
                f = 0xFF
            }
        LdRA.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.tStates).isEqualTo(9L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdRA.mnemonic { 0 }).isEqualTo("LD R, A")
    }
}
