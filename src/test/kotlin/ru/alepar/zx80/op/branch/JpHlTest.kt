package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class JpHlTest {
    @Test
    fun `JP (HL) sets pc to HL value (NOT memory at HL)`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                tStates = 0L
                hl = 0x4000
            }
        // Specifically poke memory at HL with a different value to ensure
        // we're using HL itself, not dereferencing it.
        val mem =
            Memory().apply {
                write(0x4000, 0x99)
                write(0x4001, 0x99)
            }
        JpHl.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x4000) // HL value, not memory contents
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `JP (HL) does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                hl = 0x100
                f = 0xFF
            }
        JpHl.execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JpHl.mnemonic { 0 }).isEqualTo("JP (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(JpHl.operandLength).isZero
        assertThat(JpHl.baseCycles).isEqualTo(4)
    }
}
