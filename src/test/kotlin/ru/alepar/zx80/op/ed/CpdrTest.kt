package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class CpdrTest {
    @Test
    fun `CPDR loops while BC != 0 and no match (decrements HL)`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0003
                a = 0x42
                pc = 0x100
                tStates = 0L
            }
        val mem = Memory().apply { write(0x4000, 0xAA) }
        Cpdr.execute(cpu, mem)
        assertThat(cpu.hl).isEqualTo(0x3FFF)
        assertThat(cpu.bc).isEqualTo(0x0002)
        assertThat(cpu.pc).isEqualTo(0x100)
        assertThat(cpu.tStates).isEqualTo(21L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Cpdr.mnemonic { 0 }).isEqualTo("CPDR")
    }
}
