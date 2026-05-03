package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdirTest {
    @Test
    fun `LDIR with BC=2 first iteration leaves PC unchanged so dispatcher re-fires`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                de = 0x5000
                bc = 0x0002
            }
        val mem =
            Memory().apply {
                write(0x4000, 0xAA)
                write(0x4001, 0xBB)
            }
        Ldir.execute(cpu, mem)
        assertThat(mem.read(0x5000)).isEqualTo(0xAA)
        assertThat(cpu.bc).isEqualTo(0x0001)
        assertThat(cpu.pc).isEqualTo(0x100)
        assertThat(cpu.tStates).isEqualTo(21L)
    }

    @Test
    fun `LDIR with BC=1 final iteration advances PC by 2 and uses 16 T-states`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                de = 0x5000
                bc = 0x0001
                pc = 0x100
                tStates = 0L
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Ldir.execute(cpu, mem)
        assertThat(cpu.bc).isZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `LDIR end-to-end via two execute calls`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                de = 0x5000
                bc = 0x0002
                pc = 0x100
                tStates = 0L
            }
        val mem =
            Memory().apply {
                write(0x4000, 0xAA)
                write(0x4001, 0xBB)
            }
        Ldir.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x100)
        Ldir.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(mem.read(0x5000)).isEqualTo(0xAA)
        assertThat(mem.read(0x5001)).isEqualTo(0xBB)
        assertThat(cpu.bc).isZero
        assertThat(cpu.tStates).isEqualTo(21L + 16L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ldir.mnemonic { 0 }).isEqualTo("LDIR")
    }
}
