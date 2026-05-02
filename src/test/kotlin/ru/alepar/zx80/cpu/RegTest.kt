package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegTest {
    @Test
    fun `read returns the corresponding Cpu field`() {
        val cpu =
            Cpu().apply {
                b = 0x11
                c = 0x22
                d = 0x33
                e = 0x44
                h = 0x55
                l = 0x66
                a = 0x77
            }
        assertThat(Reg.B.read(cpu)).isEqualTo(0x11)
        assertThat(Reg.C.read(cpu)).isEqualTo(0x22)
        assertThat(Reg.D.read(cpu)).isEqualTo(0x33)
        assertThat(Reg.E.read(cpu)).isEqualTo(0x44)
        assertThat(Reg.H.read(cpu)).isEqualTo(0x55)
        assertThat(Reg.L.read(cpu)).isEqualTo(0x66)
        assertThat(Reg.A.read(cpu)).isEqualTo(0x77)
    }

    @Test
    fun `write updates the corresponding Cpu field`() {
        val cpu = Cpu()
        Reg.B.write(cpu, 0x11)
        Reg.C.write(cpu, 0x22)
        Reg.D.write(cpu, 0x33)
        Reg.E.write(cpu, 0x44)
        Reg.H.write(cpu, 0x55)
        Reg.L.write(cpu, 0x66)
        Reg.A.write(cpu, 0x77)
        assertThat(cpu.b).isEqualTo(0x11)
        assertThat(cpu.c).isEqualTo(0x22)
        assertThat(cpu.d).isEqualTo(0x33)
        assertThat(cpu.e).isEqualTo(0x44)
        assertThat(cpu.h).isEqualTo(0x55)
        assertThat(cpu.l).isEqualTo(0x66)
        assertThat(cpu.a).isEqualTo(0x77)
    }

    @Test
    fun `write masks to 8 bits`() {
        val cpu = Cpu()
        Reg.A.write(cpu, 0x1FF)
        assertThat(cpu.a).isEqualTo(0xFF)

        Reg.B.write(cpu, -1)
        assertThat(cpu.b).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic matches the canonical Z80 register name`() {
        assertThat(Reg.B.mnemonic).isEqualTo("B")
        assertThat(Reg.C.mnemonic).isEqualTo("C")
        assertThat(Reg.D.mnemonic).isEqualTo("D")
        assertThat(Reg.E.mnemonic).isEqualTo("E")
        assertThat(Reg.H.mnemonic).isEqualTo("H")
        assertThat(Reg.L.mnemonic).isEqualTo("L")
        assertThat(Reg.A.mnemonic).isEqualTo("A")
    }
}
