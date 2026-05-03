package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdAddrFromATest {
    @Test
    fun `LD (nn), A writes A to memory at little-endian address`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                tStates = 0L
                a = 0x42
                f = 0xAA
            }
        val mem =
            Memory().apply {
                write(0x100, 0x32)
                write(0x101, 0x00)
                write(0x102, 0x40)
            }
        LdAddrFromA.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x42)
        assertThat(cpu.a).isEqualTo(0x42) // unchanged
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.tStates).isEqualTo(13L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAddrFromA.mnemonic { 0 }).isEqualTo("LD (nn), A")
    }

    @Test
    fun `operandLength=2, baseCycles=13`() {
        assertThat(LdAddrFromA.operandLength).isEqualTo(2)
        assertThat(LdAddrFromA.baseCycles).isEqualTo(13)
    }
}
