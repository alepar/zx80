package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class AluARegTest {
    @Test
    fun `ADD A, B updates A and F, advances pc, increments r, adds 4 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x05
                b = 0x03
            }
        AluAReg(op = AluOp.ADD, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x08)
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.f and Flags.Z).isZero
        assertThat(cpu.b).isEqualTo(0x03)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `ADC A, B folds in carry`() {
        val cpu =
            Cpu().apply {
                a = 0x05
                b = 0x03
                f = Flags.C
            }
        AluAReg(op = AluOp.ADC, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x09)
    }

    @Test
    fun `SUB A, B sets N`() {
        val cpu =
            Cpu().apply {
                a = 0x05
                b = 0x03
            }
        AluAReg(op = AluOp.SUB, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x02)
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `CP A, B does NOT update A but sets flags`() {
        val cpu =
            Cpu().apply {
                a = 0x05
                b = 0x05
            }
        AluAReg(op = AluOp.CP, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x05)
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `AND A, B clears C, sets H`() {
        val cpu =
            Cpu().apply {
                a = 0xFF
                b = 0x0F
                f = Flags.C
            }
        AluAReg(op = AluOp.AND, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x0F)
        assertThat(cpu.f and Flags.C).isZero
        assertThat(cpu.f and Flags.H).isNotZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(AluAReg(op = AluOp.ADD, src = Reg.B).mnemonic { 0 }).isEqualTo("ADD A, B")
        assertThat(AluAReg(op = AluOp.CP, src = Reg.A).mnemonic { 0 }).isEqualTo("CP A, A")
    }

    @Test
    fun `operandLength is 0, baseCycles is 4`() {
        val op = AluAReg(op = AluOp.ADD, src = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(4)
    }
}
