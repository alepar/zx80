package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class IncRegTest {
    @Test
    fun `INC B updates B and F, advances pc, increments r, adds 4 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x05
            }
        IncReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x06)
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `INC preserves C flag`() {
        val cpu =
            Cpu().apply {
                b = 0x05
                f = Flags.C
            }
        IncReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `INC 0xFF wraps to 0x00, sets Z and H, NOT C`() {
        val cpu = Cpu().apply { b = 0xFF }
        IncReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(IncReg(dst = Reg.B).mnemonic { 0 }).isEqualTo("INC B")
        assertThat(IncReg(dst = Reg.A).mnemonic { 0 }).isEqualTo("INC A")
    }

    @Test
    fun `operandLength is 0, baseCycles is 4`() {
        val op = IncReg(dst = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(4)
    }
}
