package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class ScfTest {
    @Test
    fun `SCF sets C, clears H and N, preserves S Z PV, leaves A untouched`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x42
                f = Flags.S or Flags.Z or Flags.PV or Flags.H or Flags.N
            }
        Scf.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Scf.mnemonic { 0 }).isEqualTo("SCF")
    }
}
