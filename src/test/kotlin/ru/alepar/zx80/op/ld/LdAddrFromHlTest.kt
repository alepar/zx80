package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdAddrFromHlTest {
    @Test
    fun `LD (nn), HL writes HL as little-endian word to memory at nn`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                tStates = 0L
                hl = 0xABCD
                f = 0xAA
            }
        val mem =
            Memory().apply {
                write(0x100, 0x22)
                write(0x101, 0x00)
                write(0x102, 0x40)
            }
        LdAddrFromHl.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0xCD) // low byte = L
        assertThat(mem.read(0x4001)).isEqualTo(0xAB) // high byte = H
        assertThat(cpu.hl).isEqualTo(0xABCD) // unchanged
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.tStates).isEqualTo(16L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAddrFromHl.mnemonic { 0 }).isEqualTo("LD (nn), HL")
    }

    @Test
    fun `operandLength=2, baseCycles=16`() {
        assertThat(LdAddrFromHl.operandLength).isEqualTo(2)
        assertThat(LdAddrFromHl.baseCycles).isEqualTo(16)
    }
}
