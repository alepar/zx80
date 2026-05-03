package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class LdHlMemImmTest {
    @Test
    fun `LD (HL), n writes immediate byte into memory at HL`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                f = 0xAA
            }
        val mem =
            Memory().apply {
                write(0x100, 0x36) // LD (HL), n opcode
                write(0x101, 0x42) // immediate value
            }
        LdHlMemImm.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x42)
        assertThat(cpu.hl).isEqualTo(0x4000) // hl unchanged
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(10L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdHlMemImm.mnemonic { 0 }).isEqualTo("LD (HL), n")
    }

    @Test
    fun `operandLength=1, baseCycles=10`() {
        assertThat(LdHlMemImm.operandLength).isEqualTo(1)
        assertThat(LdHlMemImm.baseCycles).isEqualTo(10)
    }
}
