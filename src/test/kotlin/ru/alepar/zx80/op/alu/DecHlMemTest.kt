package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class DecHlMemTest {
    @Test
    fun `DEC (HL) decrements byte at HL, sets N, 11 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
            }
        val mem = Memory().apply { write(0x4000, 0x05) }
        DecHlMem.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x04)
        assertThat(cpu.f and Flags.N).isNotZero
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `DEC (HL) preserves C flag`() {
        val cpu =
            Cpu().apply {
                hl = 0x100
                f = Flags.C
            }
        val mem = Memory().apply { write(0x100, 0x05) }
        DecHlMem.execute(cpu, mem)
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(DecHlMem.mnemonic { 0 }).isEqualTo("DEC (HL)")
    }

    @Test
    fun `operandLength is 0, baseCycles is 11`() {
        assertThat(DecHlMem.operandLength).isZero
        assertThat(DecHlMem.baseCycles).isEqualTo(11)
    }
}
