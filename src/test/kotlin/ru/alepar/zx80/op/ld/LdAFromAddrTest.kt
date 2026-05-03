package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdAFromAddrTest {
    @Test
    fun `LD A, (nn) reads byte at little-endian address into A`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0
                f = 0xAA
            }
        val mem =
            Memory().apply {
                write(0x100, 0x3A) // opcode
                write(0x101, 0x00) // low byte of nn
                write(0x102, 0x40) // high byte of nn (so nn = 0x4000)
                write(0x4000, 0x42)
            }
        LdAFromAddr.execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.tStates).isEqualTo(13L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAFromAddr.mnemonic { 0 }).isEqualTo("LD A, (nn)")
    }

    @Test
    fun `operandLength=2, baseCycles=13`() {
        assertThat(LdAFromAddr.operandLength).isEqualTo(2)
        assertThat(LdAFromAddr.baseCycles).isEqualTo(13)
    }
}
