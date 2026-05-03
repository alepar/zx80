package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
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

    @Test
    fun `IND X and Y reflect (portByte + (C-1)) and 0x28`() {
        // default IoBus returns 0xFF; C = 0x81 -> (0xFF + 0x80) and 0xFF = 0x7F; 0x7F and 0x28 =
        // 0x28
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0181
            }
        Ind.execute(cpu, Memory())
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isNotZero
    }
}
