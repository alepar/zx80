package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegFromHlTest {
    @Test
    fun `LD B, (HL) copies byte at HL into B`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                b = 0x00
                f = 0xAA
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        LdRegFromHl(dst = Reg.B).execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x42)
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD A, (HL) works for the A destination`() {
        val cpu =
            Cpu().apply {
                hl = 0x100
                a = 0
            }
        val mem = Memory().apply { write(0x100, 0x99) }
        LdRegFromHl(dst = Reg.A).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x99)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdRegFromHl(dst = Reg.B).mnemonic { 0 }).isEqualTo("LD B, (HL)")
        assertThat(LdRegFromHl(dst = Reg.A).mnemonic { 0 }).isEqualTo("LD A, (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=7`() {
        val op = LdRegFromHl(dst = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
