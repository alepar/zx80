package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class SetHlTest {
    @Test
    fun `SET 7 (HL) reads, sets bit 7, writes back, 15 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                f = 0xFF
            }
        val mem = Memory().apply { write(0x4000, 0x00) }
        SetHl(n = 7).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x80)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(15L)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(SetHl(n = 3).mnemonic { 0 }).isEqualTo("SET 3, (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = SetHl(n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
