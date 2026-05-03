package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class IndrTest {
    @Test
    fun `INDR decrements HL and loops while B != 0`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0200
                pc = 0x100
                tStates = 0L
            }
        Indr.execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x3FFF)
        assertThat(cpu.pc).isEqualTo(0x100)
        assertThat(cpu.tStates).isEqualTo(21L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Indr.mnemonic { 0 }).isEqualTo("INDR")
    }
}
