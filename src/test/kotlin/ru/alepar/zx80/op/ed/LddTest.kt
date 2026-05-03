package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class LddTest {
    @Test
    fun `LDD decrements HL and DE`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                de = 0x5000
                bc = 0x0003
                pc = 0x100
                tStates = 0L
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Ldd.execute(cpu, mem)
        assertThat(mem.read(0x5000)).isEqualTo(0x42)
        assertThat(cpu.hl).isEqualTo(0x3FFF)
        assertThat(cpu.de).isEqualTo(0x4FFF)
        assertThat(cpu.bc).isEqualTo(0x0002)
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.tStates).isEqualTo(16L)
        assertThat(cpu.pc).isEqualTo(0x102)
    }

    @Test
    fun `LDD wraps HL from 0 to 0xFFFF`() {
        val cpu =
            Cpu().apply {
                hl = 0
                de = 0
                bc = 1
            }
        Ldd.execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0xFFFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ldd.mnemonic { 0 }).isEqualTo("LDD")
    }

    @Test
    fun `LDD X comes from bit 1 of n=(byte+A) and Y comes from bit 3`() {
        // n = 0x22 (0010 0010): bit 1 = 1 -> X; bit 3 = 0 -> not Y.
        val cpu =
            Cpu().apply {
                hl = 0x4000
                de = 0x5000
                bc = 0x0001
                a = 0x20
            }
        val mem = Memory().apply { write(0x4000, 0x02) }
        Ldd.execute(cpu, mem)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isZero
    }
}
