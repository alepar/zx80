package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class LdAFromBcDeTest {
    @Test
    fun `LD A, (BC) reads byte at BC into A`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                bc = 0x4000
                a = 0
                f = 0xAA
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        LdAFromBcDe(pair = RegPair.BC).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.bc).isEqualTo(0x4000) // unchanged
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD A, (DE) reads byte at DE into A`() {
        val cpu =
            Cpu().apply {
                de = 0x100
                a = 0
            }
        val mem = Memory().apply { write(0x100, 0x99) }
        LdAFromBcDe(pair = RegPair.DE).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x99)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAFromBcDe(pair = RegPair.BC).mnemonic { 0 }).isEqualTo("LD A, (BC)")
        assertThat(LdAFromBcDe(pair = RegPair.DE).mnemonic { 0 }).isEqualTo("LD A, (DE)")
    }

    @Test
    fun `operandLength=0, baseCycles=7`() {
        val op = LdAFromBcDe(pair = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(7)
    }

    @Test
    fun `LdAFromBcDe rejects HL or SP`() {
        assertThatThrownBy { LdAFromBcDe(pair = RegPair.HL) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { LdAFromBcDe(pair = RegPair.SP) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
