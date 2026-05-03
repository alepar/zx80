package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RlaTest {
    @Test
    fun `RLA folds old C into bit 0, old bit 7 becomes new C`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x80
                f = Flags.C
            }
        Rla.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x01)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `RLA with C=0 and bit 7 clear gives no carry`() {
        val cpu =
            Cpu().apply {
                a = 0x42
                f = 0
            }
        Rla.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x84)
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `RLA preserves S Z PV from oldF`() {
        val cpu =
            Cpu().apply {
                a = 0x42
                f = Flags.S or Flags.Z or Flags.PV
            }
        Rla.execute(cpu, Memory())
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Rla.mnemonic { 0 }).isEqualTo("RLA")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(Rla.operandLength).isZero
        assertThat(Rla.baseCycles).isEqualTo(4)
    }
}
