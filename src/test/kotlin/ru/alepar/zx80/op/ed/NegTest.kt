package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class NegTest {
    @Test
    fun `NEG of 0 leaves A=0, sets Z, no carry`() {
        val cpu = Cpu().apply { a = 0 }
        Neg.execute(cpu, Memory())
        assertThat(cpu.a).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.C).isZero
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `NEG of 1 yields 0xFF, sets S and C`() {
        val cpu = Cpu().apply { a = 0x01 }
        Neg.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xFF)
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `NEG of 0x80 leaves A=0x80, sets P-V (overflow)`() {
        val cpu = Cpu().apply { a = 0x80 }
        Neg.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x80)
        assertThat(cpu.f and Flags.PV).isNotZero
    }

    @Test
    fun `NEG advances pc by 2, r by 2, 8 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x42
            }
        Neg.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Neg.mnemonic { 0 }).isEqualTo("NEG")
    }
}
