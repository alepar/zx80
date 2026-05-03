package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RrcaTest {
    @Test
    fun `RRCA rotates A right, bit 0 to C and to bit 7`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x85
            }
        Rrca.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xC2)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `RRCA with bit 0 clear leaves C clear`() {
        val cpu = Cpu().apply { a = 0x42 }
        Rrca.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x21)
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `RRCA preserves S Z PV from oldF`() {
        val cpu =
            Cpu().apply {
                a = 0x42
                f = Flags.S or Flags.Z or Flags.PV
            }
        Rrca.execute(cpu, Memory())
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Rrca.mnemonic { 0 }).isEqualTo("RRCA")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(Rrca.operandLength).isZero
        assertThat(Rrca.baseCycles).isEqualTo(4)
    }
}
