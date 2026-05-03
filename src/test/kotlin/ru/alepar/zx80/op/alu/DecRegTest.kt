package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class DecRegTest {
    @Test
    fun `DEC B updates B and F, sets N, advances pc, increments r, adds 4 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x05
            }
        DecReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x04)
        assertThat(cpu.f and Flags.N).isNotZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `DEC preserves C flag`() {
        val cpu =
            Cpu().apply {
                b = 0x05
                f = Flags.C
            }
        DecReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `DEC 0x01 sets Z`() {
        val cpu = Cpu().apply { b = 0x01 }
        DecReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `DEC 0x00 wraps to 0xFF, sets H and S, NOT C`() {
        val cpu = Cpu().apply { b = 0x00 }
        DecReg(dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0xFF)
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(DecReg(dst = Reg.A).mnemonic { 0 }).isEqualTo("DEC A")
    }

    @Test
    fun `operandLength is 0, baseCycles is 4`() {
        val op = DecReg(dst = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(4)
    }
}
