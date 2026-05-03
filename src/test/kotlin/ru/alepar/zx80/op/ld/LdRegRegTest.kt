package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegRegTest {
    @Test
    fun `LD B, C copies C into B and leaves flags untouched`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x11
                c = 0x22
                f = 0xAA
            }
        LdRegReg(src = Reg.C, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x22)
        assertThat(cpu.c).isEqualTo(0x22)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD A, A is a no-op on register state but still ticks pc, r, tStates`() {
        val cpu =
            Cpu().apply {
                a = 0x42
                pc = 0x100
                tStates = 0L
            }
        LdRegReg(src = Reg.A, dst = Reg.A).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic format is LD dst, src`() {
        assertThat(LdRegReg(src = Reg.C, dst = Reg.B).mnemonic { 0 }).isEqualTo("LD B, C")
        assertThat(LdRegReg(src = Reg.A, dst = Reg.L).mnemonic { 0 }).isEqualTo("LD L, A")
    }

    @Test
    fun `operandLength is 0 and baseCycles is 4`() {
        val op = LdRegReg(src = Reg.B, dst = Reg.C)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(4)
    }
}
