package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RldTest {
    @Test
    fun `RLD A=0x7A (HL)=0x31 → A=0x73 (HL)=0x1A`() {
        val cpu =
            Cpu().apply {
                a = 0x7A
                hl = 0x4000
                f = Flags.C
            }
        val mem = Memory().apply { write(0x4000, 0x31) }
        Rld.execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x73)
        assertThat(mem.read(0x4000)).isEqualTo(0x1A)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.tStates).isEqualTo(18L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Rld.mnemonic { 0 }).isEqualTo("RLD")
    }

    @Test
    fun `RLD sets X and Y from new A bits 5 and 3`() {
        val cpu =
            Cpu().apply {
                a = 0x22 // high nibble keeps after the rotate
                hl = 0x4000
            }
        val mem = Memory().apply { write(0x4000, 0x88) }
        Rld.execute(cpu, mem)
        // RLD: new mem high nibble = old mem low; new mem low = old A low; new A low = old mem high
        // new A = (0x22 & 0xF0) | ((0x88 >> 4) & 0xF) = 0x20 | 0x8 = 0x28 -> X+Y
        assertThat(cpu.a).isEqualTo(0x28)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isNotZero
    }
}
