package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class OutdTest {
    @Test
    fun `OUTD decrements HL`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0300
            }
        Outd.execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x3FFF)
        assertThat(cpu.b).isEqualTo(0x02)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Outd.mnemonic { 0 }).isEqualTo("OUTD")
    }

    @Test
    fun `OUTD X and Y come from bits 5 and 3 of B after decrement`() {
        // B = 0x29 -> after = 0x28: bit 5 = 1 -> X; bit 3 = 1 -> Y.
        val cpu =
            Cpu().apply {
                hl = 0x4018
                bc = 0x2907
            }
        val mem = Memory().apply { write(0x4018, 0x10) }
        Outd.execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x28)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isNotZero
    }

    @Test
    fun `OUTD N comes from bit 7 of byte read from HL`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0200
            }
        val mem = Memory().apply { write(0x4000, 0x80) }
        Outd.execute(cpu, mem)
        assertThat(cpu.f and Flags.N).isNotZero
    }
}
