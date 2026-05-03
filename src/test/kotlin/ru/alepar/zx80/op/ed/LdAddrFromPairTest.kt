package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class LdAddrFromPairTest {
    @Test
    fun `LD (nn), BC writes BC as little-endian word, advances pc by 4, r by 2, 20 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                bc = 0xABCD
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x100, 0xED)
                write(0x101, 0x43)
                write(0x102, 0x00)
                write(0x103, 0x40)
            }
        LdAddrFromPair(pair = RegPair.BC).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0xCD)
        assertThat(mem.read(0x4001)).isEqualTo(0xAB)
        assertThat(cpu.bc).isEqualTo(0xABCD)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(20L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `LD (nn), SP works for SP`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                sp = 0x1234
            }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x103, 0x40)
            }
        LdAddrFromPair(pair = RegPair.SP).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x34)
        assertThat(mem.read(0x4001)).isEqualTo(0x12)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAddrFromPair(pair = RegPair.BC).mnemonic { 0 }).isEqualTo("LD (nn), BC")
        assertThat(LdAddrFromPair(pair = RegPair.SP).mnemonic { 0 }).isEqualTo("LD (nn), SP")
    }

    @Test
    fun `rejects AF`() {
        assertThatThrownBy { LdAddrFromPair(pair = RegPair.AF) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `operandLength=2, baseCycles=20`() {
        val op = LdAddrFromPair(pair = RegPair.BC)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(20)
    }
}
