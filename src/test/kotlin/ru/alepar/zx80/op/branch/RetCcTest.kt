package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RetCcTest {
    @Test
    fun `RET Z taken pops pc, 11 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0x3FFE
                f = Flags.Z
            }
        val mem =
            Memory().apply {
                write(0x3FFE, 0x34)
                write(0x3FFF, 0x12)
            }
        RetCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x1234)
        assertThat(cpu.sp).isEqualTo(0x4000)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `RET Z not-taken falls through (pc+1), no pop, 5 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                tStates = 0L
                sp = 0x3FFE
                f = 0
            }
        val mem =
            Memory().apply {
                write(0x3FFE, 0x34)
                write(0x3FFF, 0x12)
            }
        RetCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(cpu.tStates).isEqualTo(5L)
    }

    @Test
    fun `RET cc does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                sp = 0x3FFE
                f = Flags.Z
            }
        val mem =
            Memory().apply {
                write(0x3FFE, 0x00)
                write(0x3FFF, 0x40)
            }
        RetCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(Flags.Z)
    }

    @Test
    fun `mnemonic`() {
        assertThat(RetCc(cond = Condition.NZ).mnemonic { 0 }).isEqualTo("RET NZ")
        assertThat(RetCc(cond = Condition.M).mnemonic { 0 }).isEqualTo("RET M")
    }

    @Test
    fun `operandLength=0, baseCycles=5 (not-taken cost, taken adds 6)`() {
        val op = RetCc(cond = Condition.NZ)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(5)
    }
}
