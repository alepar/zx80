package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class IncHlMemTest {
    @Test
    fun `INC (HL) increments byte at HL, 11 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
            }
        val mem = Memory().apply { write(0x4000, 0x05) }
        IncHlMem.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x06)
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(11L)
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `INC (HL) preserves C flag`() {
        val cpu =
            Cpu().apply {
                hl = 0x100
                f = Flags.C
            }
        val mem = Memory().apply { write(0x100, 0x05) }
        IncHlMem.execute(cpu, mem)
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(IncHlMem.mnemonic { 0 }).isEqualTo("INC (HL)")
    }

    @Test
    fun `operandLength is 0, baseCycles is 11`() {
        assertThat(IncHlMem.operandLength).isZero
        assertThat(IncHlMem.baseCycles).isEqualTo(11)
    }
}
