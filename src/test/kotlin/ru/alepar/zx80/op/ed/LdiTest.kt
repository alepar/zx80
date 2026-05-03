package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class LdiTest {
    @Test
    fun `LDI copies (HL) to (DE), increments HL and DE, decrements BC, 16 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                de = 0x5000
                bc = 0x0003
                f = Flags.S or Flags.Z or Flags.C
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Ldi.execute(cpu, mem)
        assertThat(mem.read(0x5000)).isEqualTo(0x42)
        assertThat(cpu.hl).isEqualTo(0x4001)
        assertThat(cpu.de).isEqualTo(0x5001)
        assertThat(cpu.bc).isEqualTo(0x0002)
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `LDI clears PV when BC becomes 0`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                de = 0x5000
                bc = 0x0001
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Ldi.execute(cpu, mem)
        assertThat(cpu.bc).isZero
        assertThat(cpu.f and Flags.PV).isZero
    }

    @Test
    fun `LDI handles HL wrap (0xFFFF to 0x0000)`() {
        val cpu =
            Cpu().apply {
                hl = 0xFFFF
                de = 0x5000
                bc = 1
            }
        val mem = Memory().apply { write(0xFFFF, 0x42) }
        Ldi.execute(cpu, mem)
        assertThat(cpu.hl).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ldi.mnemonic { 0 }).isEqualTo("LDI")
    }

    @Test
    fun `LDI X comes from bit 1 of n=(byte+A) and Y comes from bit 3`() {
        // Per Sean Young's TUZD: F[5] (X) = bit 1 of n; F[3] (Y) = bit 3 of n.
        // Pick n = 0x22 (0010 0010): bit 1 = 1 -> X set; bit 3 = 0 -> Y clear.
        val cpu =
            Cpu().apply {
                hl = 0x4000
                de = 0x5000
                bc = 0x0001
                a = 0x10
            }
        val mem = Memory().apply { write(0x4000, 0x12) } // n = 0x12 + 0x10 = 0x22
        Ldi.execute(cpu, mem)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isZero
    }

    @Test
    fun `LDI Y comes from bit 3 of n and X comes from bit 1`() {
        // Pick n = 0x08 (0000 1000): bit 1 = 0 -> X clear; bit 3 = 1 -> Y set.
        val cpu =
            Cpu().apply {
                hl = 0x4000
                de = 0x5000
                bc = 0x0001
                a = 0x00
            }
        val mem = Memory().apply { write(0x4000, 0x08) }
        Ldi.execute(cpu, mem)
        assertThat(cpu.f and Flags.X).isZero
        assertThat(cpu.f and Flags.Y).isNotZero
    }
}
