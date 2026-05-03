package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LddrTest {
    @Test
    fun `LDDR loops while BC != 0`() {
        val cpu =
            Cpu().apply {
                hl = 0x4001
                de = 0x5001
                bc = 0x0002
                pc = 0x100
                tStates = 0L
            }
        val mem =
            Memory().apply {
                write(0x4000, 0xAA)
                write(0x4001, 0xBB)
            }
        Lddr.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x100)
        assertThat(cpu.tStates).isEqualTo(21L)
        Lddr.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.bc).isZero
        assertThat(mem.read(0x5001)).isEqualTo(0xBB)
        assertThat(mem.read(0x5000)).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Lddr.mnemonic { 0 }).isEqualTo("LDDR")
    }
}
