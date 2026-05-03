package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdHlFromAddrTest {
    @Test
    fun `LD HL, (nn) reads little-endian word at nn into HL`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                tStates = 0L
                hl = 0
                f = 0xAA
            }
        val mem =
            Memory().apply {
                write(0x100, 0x2A)
                write(0x101, 0x00) // low byte of nn
                write(0x102, 0x40) // high byte of nn (nn = 0x4000)
                write(0x4000, 0xCD) // becomes L
                write(0x4001, 0xAB) // becomes H
            }
        LdHlFromAddr.execute(cpu, mem)
        assertThat(cpu.l).isEqualTo(0xCD)
        assertThat(cpu.h).isEqualTo(0xAB)
        assertThat(cpu.hl).isEqualTo(0xABCD)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.tStates).isEqualTo(16L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdHlFromAddr.mnemonic { 0 }).isEqualTo("LD HL, (nn)")
    }

    @Test
    fun `operandLength=2, baseCycles=16`() {
        assertThat(LdHlFromAddr.operandLength).isEqualTo(2)
        assertThat(LdHlFromAddr.baseCycles).isEqualTo(16)
    }
}
