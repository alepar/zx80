package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class AddHlPairTest {
    @Test
    fun `ADD HL, BC adds BC to HL, advances pc, increments r, adds 11 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x1234
                bc = 0x5678
            }
        AddHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x68AC)
        assertThat(cpu.bc).isEqualTo(0x5678)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `ADD HL preserves S, Z, PV from oldF`() {
        val cpu =
            Cpu().apply {
                hl = 0x0001
                bc = 0x0001
                f = Flags.S or Flags.Z or Flags.PV
            }
        AddHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `ADD HL, HL doubles HL`() {
        val cpu = Cpu().apply { hl = 0x4000 }
        AddHlPair(src = RegPair.HL).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x8000)
    }

    @Test
    fun `ADD HL, SP works`() {
        val cpu =
            Cpu().apply {
                hl = 0x1000
                sp = 0x2000
            }
        AddHlPair(src = RegPair.SP).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x3000)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(AddHlPair(src = RegPair.BC).mnemonic { 0 }).isEqualTo("ADD HL, BC")
        assertThat(AddHlPair(src = RegPair.SP).mnemonic { 0 }).isEqualTo("ADD HL, SP")
    }

    @Test
    fun `operandLength=0, baseCycles=11`() {
        val op = AddHlPair(src = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(11)
    }
}
