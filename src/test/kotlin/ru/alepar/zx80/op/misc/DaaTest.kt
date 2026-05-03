package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class DaaTest {
    @Test
    fun `DAA after ADD 0x0A becomes 0x10 with H, advances pc, increments r, adds 4 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x0A
                f = 0
            }
        Daa.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x10)
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `DAA preserves N flag`() {
        val cpu =
            Cpu().apply {
                a = 0x42
                f = Flags.N
            }
        Daa.execute(cpu, Memory())
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Daa.mnemonic { 0 }).isEqualTo("DAA")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(Daa.operandLength).isZero
        assertThat(Daa.baseCycles).isEqualTo(4)
    }
}
