package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class LdPairFromAddrTest {
    @Test
    fun `LD BC, (nn) reads little-endian word from memory, advances pc by 4, 20 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                bc = 0
            }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x103, 0x40)
                write(0x4000, 0xCD)
                write(0x4001, 0xAB)
            }
        LdPairFromAddr(pair = RegPair.BC).execute(cpu, mem)
        assertThat(cpu.bc).isEqualTo(0xABCD)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(20L)
    }

    @Test
    fun `LD SP, (nn) works for SP`() {
        val cpu = Cpu().apply { pc = 0x100 }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x103, 0x40)
                write(0x4000, 0x34)
                write(0x4001, 0x12)
            }
        LdPairFromAddr(pair = RegPair.SP).execute(cpu, mem)
        assertThat(cpu.sp).isEqualTo(0x1234)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdPairFromAddr(pair = RegPair.BC).mnemonic { 0 }).isEqualTo("LD BC, (nn)")
        assertThat(LdPairFromAddr(pair = RegPair.SP).mnemonic { 0 }).isEqualTo("LD SP, (nn)")
    }
}
