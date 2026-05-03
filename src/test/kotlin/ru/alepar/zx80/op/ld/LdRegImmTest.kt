package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegImmTest {
    @Test
    fun `LD B, n reads the immediate byte into B and advances pc by 2`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x00
                f = 0xAA
            }
        val mem =
            Memory().apply {
                write(0x100, 0x06) // LD B, n opcode
                write(0x101, 0x42) // immediate value
            }
        LdRegImm(dst = Reg.B).execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD A, n works for A`() {
        val cpu = Cpu().apply { pc = 0x200 }
        val mem =
            Memory().apply {
                write(0x200, 0x3E)
                write(0x201, 0x99)
            }
        LdRegImm(dst = Reg.A).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x99)
        assertThat(cpu.pc).isEqualTo(0x202)
    }

    @Test
    fun `LD r, n wraps pc mod 64K`() {
        val cpu = Cpu().apply { pc = 0xFFFE }
        val mem =
            Memory().apply {
                write(0xFFFE, 0x06)
                write(0xFFFF, 0x42)
            }
        LdRegImm(dst = Reg.B).execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x0000)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdRegImm(dst = Reg.B).mnemonic { 0 }).isEqualTo("LD B, n")
        assertThat(LdRegImm(dst = Reg.A).mnemonic { 0 }).isEqualTo("LD A, n")
    }

    @Test
    fun `operandLength=1, baseCycles=7`() {
        val op = LdRegImm(dst = Reg.B)
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
