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

    @Test
    fun `CPI sets X and Y from (A - mem at HL - H_after) bits 5 and 3`() {
        val cpu =
            Cpu().apply {
                a = 0x30
                hl = 0x4000
                bc = 0x0001
            }
        val mem = Memory().apply { write(0x4000, 0x08) }
        Cpi.execute(cpu, mem)
        // diff = 0x30 - 0x08 = 0x28; H computed: (0x30 & 0xF) - (0x08 & 0xF) = 0 - 8 < 0 -> H=1
        // n = 0x28 - 1 = 0x27 -> 0x27 and 0x28 = 0x20 -> only X
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isZero
    }
}
