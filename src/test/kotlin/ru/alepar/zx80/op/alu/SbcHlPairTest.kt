package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class SbcHlPairTest {
    @Test
    fun `SBC HL, BC subtracts BC + borrow, sets N, ED-prefixed`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x5000
                bc = 0x1000
                f = Flags.C
            }
        SbcHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0x3FFF)
        assertThat(cpu.f and Flags.N).isNotZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(15L)
    }

    @Test
    fun `SBC HL, HL with no carry gives 0 and sets Z`() {
        val cpu =
            Cpu().apply {
                hl = 0x1234
                f = 0
            }
        SbcHlPair(src = RegPair.HL).execute(cpu, Memory())
        assertThat(cpu.hl).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `SBC HL borrow at 0x0000 - 0x0001 wraps and sets C`() {
        val cpu =
            Cpu().apply {
                hl = 0x0000
                bc = 0x0001
                f = 0
            }
        SbcHlPair(src = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0xFFFF)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.S).isNotZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(SbcHlPair(src = RegPair.SP).mnemonic { 0 }).isEqualTo("SBC HL, SP")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = SbcHlPair(src = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
