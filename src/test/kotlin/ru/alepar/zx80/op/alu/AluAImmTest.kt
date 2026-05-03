package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class AluAImmTest {
    @Test
    fun `ADD A, n reads immediate, adds to A, advances pc by 2, 7 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x05
            }
        val mem =
            Memory().apply {
                write(0x100, 0xC6)
                write(0x101, 0x03)
            }
        AluAImm(op = AluOp.ADD).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x08)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(7L)
    }

    @Test
    fun `CP A, n does not update A`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                a = 0x42
            }
        val mem = Memory().apply { write(0x101, 0x42) }
        AluAImm(op = AluOp.CP).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(AluAImm(op = AluOp.ADD).mnemonic { 0 }).isEqualTo("ADD A, n")
        assertThat(AluAImm(op = AluOp.OR).mnemonic { 0 }).isEqualTo("OR A, n")
    }

    @Test
    fun `operandLength is 1, baseCycles is 7`() {
        val op = AluAImm(op = AluOp.ADD)
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
