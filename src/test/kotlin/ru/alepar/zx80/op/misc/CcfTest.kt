package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class CcfTest {
    @Test
    fun `CCF with C=1 toggles to C=0, H gets old C value (1)`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x42
                f = Flags.C or Flags.S
            }
        Ccf.execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isZero
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `CCF with C=0 toggles to C=1, H gets old C value (0)`() {
        val cpu = Cpu().apply { f = Flags.S or Flags.Z }
        Ccf.execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ccf.mnemonic { 0 }).isEqualTo("CCF")
    }
}
