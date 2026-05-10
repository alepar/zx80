package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class EiTest {
    @Test
    fun `Ei sets iff1 and iff2, advances pc, increments r, adds 4 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                iff1 = false
                iff2 = false
            }
        Ei.execute(cpu, Memory())
        assertThat(cpu.iff1).isTrue
        assertThat(cpu.iff2).isTrue
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ei.mnemonic { 0 }).isEqualTo("EI")
    }

    @Test
    fun `Ei sets eiPending to enable post-EI interrupt delay slot`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                eiPending = false
            }
        Ei.execute(cpu, Memory())
        assertThat(cpu.eiPending).isTrue
    }
}
