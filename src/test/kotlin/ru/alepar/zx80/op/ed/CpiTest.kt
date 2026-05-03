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
    fun `CPI X comes from bit 1 of n=(A - byte - H_after) and Y comes from bit 3`() {
        // Per Sean Young's TUZD: F[5] (X) = bit 1 of n; F[3] (Y) = bit 3 of n.
        // diff = 0x30 - 0x08 = 0x28; H = 1 (low-nibble borrow). n = 0x28 - 1 = 0x27.
        // 0x27 = 0010 0111: bit 1 = 1 -> X set; bit 3 = 0 -> Y clear.
        val cpu =
            Cpu().apply {
                a = 0x30
                hl = 0x4000
                bc = 0x0001
            }
        val mem = Memory().apply { write(0x4000, 0x08) }
        Cpi.execute(cpu, mem)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isZero
    }

    @Test
    fun `CPI Sean Young rule discriminates from old n_and_0x28 rule`() {
        // diff = 0x40 - 0x10 = 0x30; H = 0 (low nibble 0-0=0). n = 0x30 (no H subtraction).
        // 0x30 = 0011 0000. OLD rule (n & 0x28) -> X=1 (bit 5 = 1), Y=0 (bit 3 = 0).
        // NEW rule (bit 1 -> X, bit 3 -> Y) -> X=0 (bit 1 = 0), Y=0 (bit 3 = 0).
        val cpu =
            Cpu().apply {
                a = 0x40
                hl = 0x4000
                bc = 0x0001
            }
        val mem = Memory().apply { write(0x4000, 0x10) }
        Cpi.execute(cpu, mem)
        assertThat(cpu.f and Flags.X).isZero
        assertThat(cpu.f and Flags.Y).isZero
    }
}
