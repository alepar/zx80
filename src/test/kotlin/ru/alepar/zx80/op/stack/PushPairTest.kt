package ru.alepar.zx80.op.stack

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class PushPairTest {
    @Test
    fun `PUSH BC pushes BC value (high byte at SP-1, low at SP-2), SP -= 2, 11 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0x4000
                bc = 0xABCD
            }
        val mem = Memory()
        PushPair(src = RegPair.BC).execute(cpu, mem)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFE)).isEqualTo(0xCD)
        assertThat(mem.read(0x3FFF)).isEqualTo(0xAB)
        assertThat(cpu.bc).isEqualTo(0xABCD)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `PUSH AF pushes A as high, F as low`() {
        val cpu =
            Cpu().apply {
                sp = 0x4000
                a = 0x12
                f = 0x34
            }
        val mem = Memory()
        PushPair(src = RegPair.AF).execute(cpu, mem)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x34)
        assertThat(mem.read(0x3FFF)).isEqualTo(0x12)
    }

    @Test
    fun `PUSH does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                sp = 0x4000
                bc = 0x1234
                f = 0xAA
            }
        PushPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `PushPair rejects RegPair SP`() {
        assertThatThrownBy { PushPair(src = RegPair.SP) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(PushPair(src = RegPair.BC).mnemonic { 0 }).isEqualTo("PUSH BC")
        assertThat(PushPair(src = RegPair.AF).mnemonic { 0 }).isEqualTo("PUSH AF")
    }

    @Test
    fun `operandLength=0, baseCycles=11`() {
        val op = PushPair(src = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(11)
    }
}
