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
    fun `OUTD X and Y reflect (portByte + L) and 0x28`() {
        val cpu =
            Cpu().apply {
                hl = 0x4018
                bc = 0x0107
            }
        val mem = Memory().apply { write(0x4018, 0x10) }
        Outd.execute(cpu, mem)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isNotZero
    }
}
