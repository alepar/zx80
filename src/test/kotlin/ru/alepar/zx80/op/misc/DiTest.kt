package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class DiTest {
    @Test
    fun `Di clears iff1 and iff2, advances pc, increments r, adds 4 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                iff1 = true
                iff2 = true
            }
        Di.execute(cpu, Memory())
        assertThat(cpu.iff1).isFalse
        assertThat(cpu.iff2).isFalse
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `Di on already-clear iff1 iff2 stays clear`() {
        val cpu =
            Cpu().apply {
                iff1 = false
                iff2 = false
            }
        Di.execute(cpu, Memory())
        assertThat(cpu.iff1).isFalse
        assertThat(cpu.iff2).isFalse
    }

    @Test
    fun `mnemonic`() {
        assertThat(Di.mnemonic { 0 }).isEqualTo("DI")
    }
}
