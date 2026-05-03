package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class OtirTest {
    @Test
    fun `OTIR loops while B != 0`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0200
                pc = 0x100
                tStates = 0L
            }
        Otir.execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x01)
        assertThat(cpu.pc).isEqualTo(0x100)
        assertThat(cpu.tStates).isEqualTo(21L)
    }

    @Test
    fun `OTIR exits when B reaches 0`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0100
                pc = 0x100
                tStates = 0L
            }
        Otir.execute(cpu, Memory())
        assertThat(cpu.b).isZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Otir.mnemonic { 0 }).isEqualTo("OTIR")
    }
}
