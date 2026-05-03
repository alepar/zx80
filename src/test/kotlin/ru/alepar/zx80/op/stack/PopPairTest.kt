package ru.alepar.zx80.op.stack

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class PopPairTest {
    @Test
    fun `POP BC pops 16-bit value into BC, SP += 2, 10 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0x3FFE
                bc = 0
            }
        val mem =
            Memory().apply {
                write(0x3FFE, 0xCD)
                write(0x3FFF, 0xAB)
            }
        PopPair(dst = RegPair.BC).execute(cpu, mem)
        assertThat(cpu.bc).isEqualTo(0xABCD)
        assertThat(cpu.sp).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(10L)
    }

    @Test
    fun `POP AF loads A from high byte and F from low byte`() {
        val cpu = Cpu().apply { sp = 0x3FFE }
        val mem =
            Memory().apply {
                write(0x3FFE, 0x55)
                write(0x3FFF, 0xAB)
            }
        PopPair(dst = RegPair.AF).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0xAB)
        assertThat(cpu.f).isEqualTo(0x55)
        assertThat(cpu.af).isEqualTo(0xAB55)
    }

    @Test
    fun `POP non-AF pair does NOT touch f`() {
        val cpu =
            Cpu().apply {
                sp = 0x3FFE
                f = 0xAA
            }
        val mem =
            Memory().apply {
                write(0x3FFE, 0xCD)
                write(0x3FFF, 0xAB)
            }
        PopPair(dst = RegPair.HL).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xAA)
        assertThat(cpu.hl).isEqualTo(0xABCD)
    }

    @Test
    fun `PopPair rejects RegPair SP`() {
        assertThatThrownBy { PopPair(dst = RegPair.SP) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(PopPair(dst = RegPair.BC).mnemonic { 0 }).isEqualTo("POP BC")
        assertThat(PopPair(dst = RegPair.AF).mnemonic { 0 }).isEqualTo("POP AF")
    }

    @Test
    fun `operandLength=0, baseCycles=10`() {
        val op = PopPair(dst = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
