package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class CpiTest {
    @Test
    fun `CPI compares A with (HL), increments HL, decrements BC, sets N, 16T`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                bc = 0x0003
                a = 0x42
                f = Flags.C
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Cpi.execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.hl).isEqualTo(0x4001)
        assertThat(cpu.bc).isEqualTo(0x0002)
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.N).isNotZero
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `CPI clears Z when no match`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 1
                a = 0x42
            }
        val mem = Memory().apply { write(0x4000, 0xAA) }
        Cpi.execute(cpu, mem)
        assertThat(cpu.f and Flags.Z).isZero
    }

    @Test
    fun `CPI clears PV when BC reaches 0`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 1
                a = 0x42
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Cpi.execute(cpu, mem)
        assertThat(cpu.bc).isZero
        assertThat(cpu.f and Flags.PV).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Cpi.mnemonic { 0 }).isEqualTo("CPI")
    }
}
