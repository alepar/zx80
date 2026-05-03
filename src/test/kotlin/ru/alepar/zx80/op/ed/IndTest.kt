package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class IndTest {
    @Test
    fun `IND decrements HL`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0300
            }
        Ind.execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x3FFF)
        assertThat(cpu.b).isEqualTo(0x02)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ind.mnemonic { 0 }).isEqualTo("IND")
    }
}
