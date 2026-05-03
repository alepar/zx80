package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegPairTest {
    @Test
    fun `read returns the corresponding Cpu pair value`() {
        val cpu =
            Cpu().apply {
                bc = 0x1122
                de = 0x3344
                hl = 0x5566
                sp = 0x7788
            }
        assertThat(RegPair.BC.read(cpu)).isEqualTo(0x1122)
        assertThat(RegPair.DE.read(cpu)).isEqualTo(0x3344)
        assertThat(RegPair.HL.read(cpu)).isEqualTo(0x5566)
        assertThat(RegPair.SP.read(cpu)).isEqualTo(0x7788)
    }

    @Test
    fun `write updates the corresponding Cpu pair value`() {
        val cpu = Cpu()
        RegPair.BC.write(cpu, 0x1122)
        RegPair.DE.write(cpu, 0x3344)
        RegPair.HL.write(cpu, 0x5566)
        RegPair.SP.write(cpu, 0x7788)
        assertThat(cpu.bc).isEqualTo(0x1122)
        assertThat(cpu.de).isEqualTo(0x3344)
        assertThat(cpu.hl).isEqualTo(0x5566)
        assertThat(cpu.sp).isEqualTo(0x7788)
    }

    @Test
    fun `write masks to 16 bits`() {
        val cpu = Cpu()
        RegPair.BC.write(cpu, 0x12345)
        assertThat(cpu.bc).isEqualTo(0x2345)

        RegPair.SP.write(cpu, -1)
        assertThat(cpu.sp).isEqualTo(0xFFFF)
    }

    @Test
    fun `mnemonic matches canonical Z80 pair name`() {
        assertThat(RegPair.BC.mnemonic).isEqualTo("BC")
        assertThat(RegPair.DE.mnemonic).isEqualTo("DE")
        assertThat(RegPair.HL.mnemonic).isEqualTo("HL")
        assertThat(RegPair.SP.mnemonic).isEqualTo("SP")
    }

    @Test
    fun `fromBits maps register-pair bit patterns BC=0, DE=1, HL=2, SP=3`() {
        assertThat(RegPair.fromBits(0)).isEqualTo(RegPair.BC)
        assertThat(RegPair.fromBits(1)).isEqualTo(RegPair.DE)
        assertThat(RegPair.fromBits(2)).isEqualTo(RegPair.HL)
        assertThat(RegPair.fromBits(3)).isEqualTo(RegPair.SP)
    }

    @Test
    fun `fromBits masks to lowest 2 bits`() {
        assertThat(RegPair.fromBits(0xFC)).isEqualTo(RegPair.BC)
        assertThat(RegPair.fromBits(0xFD)).isEqualTo(RegPair.DE)
    }

    @Test
    fun `AF reads and writes cpu af`() {
        val cpu =
            Cpu().apply {
                a = 0x12
                f = 0x34
            }
        assertThat(RegPair.AF.read(cpu)).isEqualTo(0x1234)

        RegPair.AF.write(cpu, 0xABCD)
        assertThat(cpu.a).isEqualTo(0xAB)
        assertThat(cpu.f).isEqualTo(0xCD)
        assertThat(cpu.af).isEqualTo(0xABCD)
    }

    @Test
    fun `AF mnemonic`() {
        assertThat(RegPair.AF.mnemonic).isEqualTo("AF")
    }

    @Test
    fun `fromPushPopBits maps PUSH POP bit patterns BC=0, DE=1, HL=2, AF=3`() {
        assertThat(RegPair.fromPushPopBits(0)).isEqualTo(RegPair.BC)
        assertThat(RegPair.fromPushPopBits(1)).isEqualTo(RegPair.DE)
        assertThat(RegPair.fromPushPopBits(2)).isEqualTo(RegPair.HL)
        assertThat(RegPair.fromPushPopBits(3)).isEqualTo(RegPair.AF)
    }

    @Test
    fun `fromPushPopBits masks to lowest 2 bits`() {
        assertThat(RegPair.fromPushPopBits(0xFC)).isEqualTo(RegPair.BC)
        assertThat(RegPair.fromPushPopBits(0xFF)).isEqualTo(RegPair.AF)
    }

    @Test
    fun `fromBits still maps bits=3 to SP (unchanged)`() {
        assertThat(RegPair.fromBits(3)).isEqualTo(RegPair.SP)
    }
}
