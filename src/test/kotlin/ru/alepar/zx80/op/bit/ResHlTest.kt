package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ResHlTest {
    @Test
    fun `RES 7 (HL) reads, clears bit 7, writes back, 15 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                f = 0xFF
            }
        val mem = Memory().apply { write(0x4000, 0xFF) }
        ResHl(n = 7).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x7F)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(15L)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(ResHl(n = 0).mnemonic { 0 }).isEqualTo("RES 0, (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = ResHl(n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
