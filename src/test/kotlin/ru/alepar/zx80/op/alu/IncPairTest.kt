package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class IncPairTest {
    @Test
    fun `INC BC increments BC by 1, advances pc, increments r, adds 6 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                bc = 0x1234
            }
        IncPair(dst = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.bc).isEqualTo(0x1235)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(6L)
    }

    @Test
    fun `INC SP wraps from 0xFFFF to 0x0000`() {
        val cpu = Cpu().apply { sp = 0xFFFF }
        IncPair(dst = RegPair.SP).execute(cpu, Memory())
        assertThat(cpu.sp).isZero
    }

    @Test
    fun `INC rr does NOT affect any flag`() {
        val cpu =
            Cpu().apply {
                hl = 0x0000
                f = 0xFF
            }
        IncPair(dst = RegPair.HL).execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(IncPair(dst = RegPair.BC).mnemonic { 0 }).isEqualTo("INC BC")
        assertThat(IncPair(dst = RegPair.SP).mnemonic { 0 }).isEqualTo("INC SP")
    }

    @Test
    fun `operandLength=0, baseCycles=6`() {
        val op = IncPair(dst = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(6)
    }
}
