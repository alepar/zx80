package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class AluAFromHlTest {
    @Test
    fun `ADD A, (HL) reads byte at HL, adds to A, 7 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                a = 0x05
            }
        val mem = Memory().apply { write(0x4000, 0x03) }
        AluAFromHl(op = AluOp.ADD).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x08)
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
    }

    @Test
    fun `CP A, (HL) does not update A`() {
        val cpu =
            Cpu().apply {
                hl = 0x100
                a = 0x42
            }
        val mem = Memory().apply { write(0x100, 0x42) }
        AluAFromHl(op = AluOp.CP).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(AluAFromHl(op = AluOp.ADD).mnemonic { 0 }).isEqualTo("ADD A, (HL)")
        assertThat(AluAFromHl(op = AluOp.XOR).mnemonic { 0 }).isEqualTo("XOR A, (HL)")
    }

    @Test
    fun `operandLength is 0, baseCycles is 7`() {
        val op = AluAFromHl(op = AluOp.ADD)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
