package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class CpirTest {
    @Test
    fun `CPIR loops while BC != 0 and no match`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0003
                a = 0x42
                pc = 0x100
                tStates = 0L
            }
        val mem = Memory().apply { write(0x4000, 0xAA) }
        Cpir.execute(cpu, mem)
        assertThat(cpu.bc).isEqualTo(0x0002)
        assertThat(cpu.pc).isEqualTo(0x100)
        assertThat(cpu.tStates).isEqualTo(21L)
    }

    @Test
    fun `CPIR exits when match found`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0003
                a = 0x42
                pc = 0x100
                tStates = 0L
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Cpir.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `CPIR exits when BC reaches 0`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 1
                a = 0x42
                pc = 0x100
                tStates = 0L
            }
        val mem = Memory().apply { write(0x4000, 0xAA) }
        Cpir.execute(cpu, mem)
        assertThat(cpu.bc).isZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Cpir.mnemonic { 0 }).isEqualTo("CPIR")
    }
}
