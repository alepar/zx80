package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RraTest {
    @Test
    fun `RRA folds old C into bit 7, old bit 0 becomes new C`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x01
                f = Flags.C
            }
        Rra.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x80)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `RRA with C=0 and bit 0 clear gives no carry`() {
        val cpu =
            Cpu().apply {
                a = 0x42
                f = 0
            }
        Rra.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x21)
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `RRA preserves S Z PV from oldF`() {
        val cpu =
            Cpu().apply {
                a = 0x42
                f = Flags.S or Flags.Z or Flags.PV
            }
        Rra.execute(cpu, Memory())
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Rra.mnemonic { 0 }).isEqualTo("RRA")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(Rra.operandLength).isZero
        assertThat(Rra.baseCycles).isEqualTo(4)
    }
}
