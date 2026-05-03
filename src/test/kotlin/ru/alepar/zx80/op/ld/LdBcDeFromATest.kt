package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class LdBcDeFromATest {
    @Test
    fun `LD (BC), A writes A into memory at BC`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                bc = 0x4000
                a = 0x42
                f = 0xAA
            }
        val mem = Memory()
        LdBcDeFromA(pair = RegPair.BC).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x42)
        assertThat(cpu.a).isEqualTo(0x42) // unchanged
        assertThat(cpu.bc).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.tStates).isEqualTo(7L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD (DE), A writes A into memory at DE`() {
        val cpu =
            Cpu().apply {
                de = 0x100
                a = 0x99
            }
        val mem = Memory()
        LdBcDeFromA(pair = RegPair.DE).execute(cpu, mem)
        assertThat(mem.read(0x100)).isEqualTo(0x99)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdBcDeFromA(pair = RegPair.BC).mnemonic { 0 }).isEqualTo("LD (BC), A")
        assertThat(LdBcDeFromA(pair = RegPair.DE).mnemonic { 0 }).isEqualTo("LD (DE), A")
    }

    @Test
    fun `operandLength=0, baseCycles=7`() {
        val op = LdBcDeFromA(pair = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(7)
    }

    @Test
    fun `LdBcDeFromA rejects HL or SP`() {
        assertThatThrownBy { LdBcDeFromA(pair = RegPair.HL) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { LdBcDeFromA(pair = RegPair.SP) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
