package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdRATest {
    @Test
    fun `LD R, A leaves R equal to A (M1 increments occur before the load)`() {
        // Real Z80: ED + 4F M1 cycles tick R first, then R := A. So the final R is A's value,
        // not A + 2. (FUSE test ed4f confirms this.)
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0x55
                tStates = 0L
                a = 0xAB
            }
        LdRA.execute(cpu, Memory())
        assertThat(cpu.r).isEqualTo(0xAB)
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
