package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class CpdTest {
    @Test
    fun `CPD decrements HL`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 2
                a = 0x42
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Cpd.execute(cpu, mem)
        assertThat(cpu.hl).isEqualTo(0x3FFF)
        assertThat(cpu.bc).isEqualTo(1)
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Cpd.mnemonic { 0 }).isEqualTo("CPD")
    }
}
