package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class LdARTest {
    @Test
    fun `LD A, R copies R to A and computes flags using IFF2 for PV`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0x42
                tStates = 0L
                a = 0
                iff2 = true
                f = Flags.C
            }
        LdAR.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.tStates).isEqualTo(9L)
    }

    @Test
    fun `LD A, R does not set PV when iff2 is false`() {
        val cpu =
            Cpu().apply {
                r = 0x42
                iff2 = false
            }
        LdAR.execute(cpu, Memory())
        assertThat(cpu.f and Flags.PV).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAR.mnemonic { 0 }).isEqualTo("LD A, R")
    }
}
