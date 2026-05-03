package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RrdTest {
    @Test
    fun `RRD rotates nibbles A=0x84 (HL)=0x20 → A=0x80 (HL)=0x42`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x84
                hl = 0x4000
                f = Flags.C
            }
        val mem = Memory().apply { write(0x4000, 0x20) }
        Rrd.execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x80)
        assertThat(mem.read(0x4000)).isEqualTo(0x42)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.tStates).isEqualTo(18L)
    }

    @Test
    fun `RRD sets Z when result A is zero`() {
        val cpu =
            Cpu().apply {
                a = 0x00
                hl = 0x4000
            }
        val mem = Memory().apply { write(0x4000, 0x00) }
        Rrd.execute(cpu, mem)
        assertThat(cpu.a).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Rrd.mnemonic { 0 }).isEqualTo("RRD")
    }
}
