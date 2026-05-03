package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class AdcHlPairTest {
    @Test
    fun `ADC HL, BC adds BC + carry, ED-prefixed advances pc by 2, r by 2, 15 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x1234
                bc = 0x1000
                f = Flags.C
            }
        AdcHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x2235)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(15L)
    }

    @Test
    fun `ADC HL, HL doubles HL plus carry`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                f = Flags.C
            }
        AdcHlPair(src = RegPair.HL).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x8001)
    }

    @Test
    fun `ADC HL sets PV on overflow`() {
        val cpu =
            Cpu().apply {
                hl = 0x7FFF
                bc = 0x0001
                f = 0
            }
        AdcHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x8000)
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.S).isNotZero
    }

    @Test
    fun `ADC HL, SP works`() {
        val cpu =
            Cpu().apply {
                hl = 0x1000
                sp = 0x2000
                f = 0
            }
        AdcHlPair(src = RegPair.SP).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x3000)
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(AdcHlPair(src = RegPair.BC).mnemonic { 0 }).isEqualTo("ADC HL, BC")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = AdcHlPair(src = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
