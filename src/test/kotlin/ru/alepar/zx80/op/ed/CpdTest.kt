package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class CpdTest {
    @Test
    fun `CPD decrements HL`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 2
                a = 0x42
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Cpd.execute(cpu, mem)
        assertThat(cpu.hl).isEqualTo(0x3FFF)
        assertThat(cpu.bc).isEqualTo(1)
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Cpd.mnemonic { 0 }).isEqualTo("CPD")
    }

    @Test
    fun `CPD X comes from bit 1 of n=(A - byte - H_after) and Y comes from bit 3`() {
        // n = 0x27: bit 1 = 1 -> X; bit 3 = 0 -> not Y. Same conclusion as Cpi.
        val cpu =
            Cpu().apply {
                a = 0x30
                hl = 0x4000
                bc = 0x0001
            }
        val mem = Memory().apply { write(0x4000, 0x08) }
        Cpd.execute(cpu, mem)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isZero
    }

    @Test
    fun `CPD Sean Young rule discriminates from old n_and_0x28 rule`() {
        // n = 0x30: bit 5 = 1 (OLD rule -> X=1) but bit 1 = 0 (NEW rule -> X=0).
        val cpu =
            Cpu().apply {
                a = 0x40
                hl = 0x4000
                bc = 0x0001
            }
        val mem = Memory().apply { write(0x4000, 0x10) }
        Cpd.execute(cpu, mem)
        assertThat(cpu.f and Flags.X).isZero
        assertThat(cpu.f and Flags.Y).isZero
    }
}
