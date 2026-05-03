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
    fun `IND X and Y come from bits 5 and 3 of B after decrement`() {
        // B = 0x29 -> after = 0x28: X=1, Y=1.
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x2981
            }
        Ind.execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x28)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isNotZero
    }

    @Test
    fun `IND N comes from bit 7 of port byte`() {
        // default IoBus returns 0xFF; bit 7 = 1 -> N set.
        val cpu = Cpu().apply { bc = 0x0200 }
        Ind.execute(cpu, Memory())
        assertThat(cpu.f and Flags.N).isNotZero
    }
}
