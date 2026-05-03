package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class BitHlTest {
    @Test
    fun `BIT 7 (HL) reads byte at HL and tests bit 7, 12 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                f = 0
            }
        val mem = Memory().apply { write(0x4000, 0x80) }
        BitHl(n = 7).execute(cpu, mem)
        assertThat(cpu.f and Flags.Z).isZero
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(mem.read(0x4000)).isEqualTo(0x80)
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(12L)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(BitHl(n = 0).mnemonic { 0 }).isEqualTo("BIT 0, (HL)")
        assertThat(BitHl(n = 7).mnemonic { 0 }).isEqualTo("BIT 7, (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=12`() {
        val op = BitHl(n = 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(12)
    }
}
