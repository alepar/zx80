package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdHlFromRegTest {
    @Test
    fun `LD (HL), B writes B into memory at HL`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                b = 0x42
                f = 0xAA
            }
        val mem = Memory()
        LdHlFromReg(src = Reg.B).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x42)
        assertThat(cpu.b).isEqualTo(0x42)
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD (HL), A works for the A source`() {
        val cpu =
            Cpu().apply {
                hl = 0x100
                a = 0x99
            }
        val mem = Memory()
        LdHlFromReg(src = Reg.A).execute(cpu, mem)
        assertThat(mem.read(0x100)).isEqualTo(0x99)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdHlFromReg(src = Reg.B).mnemonic { 0 }).isEqualTo("LD (HL), B")
        assertThat(LdHlFromReg(src = Reg.A).mnemonic { 0 }).isEqualTo("LD (HL), A")
    }

    @Test
    fun `operandLength=0, baseCycles=7`() {
        val op = LdHlFromReg(src = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
