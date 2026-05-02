package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class HaltTest {
    @Test
    fun `Halt sets halted flag, advances pc by 1, increments r, adds 4 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                halted = false
            }
        Halt.execute(cpu, Memory())
        assertThat(cpu.halted).isTrue
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `Halt does not touch flags`() {
        val cpu =
            Cpu().apply {
                pc = 0
                f = 0xFF
            }
        Halt.execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Halt.mnemonic { 0 }).isEqualTo("HALT")
    }
}
