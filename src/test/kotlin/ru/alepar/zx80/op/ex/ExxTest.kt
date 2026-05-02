package ru.alepar.zx80.op.ex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ExxTest {
    @Test
    fun `Exx swaps bc with bcAlt, de with deAlt, hl with hlAlt`() {
        val cpu =
            Cpu().apply {
                b = 0x11
                c = 0x22
                d = 0x33
                e = 0x44
                h = 0x55
                l = 0x66
                bAlt = 0x77
                cAlt = 0x88
                dAlt = 0x99
                eAlt = 0xAA
                hAlt = 0xBB
                lAlt = 0xCC
            }
        Exx.execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x77)
        assertThat(cpu.c).isEqualTo(0x88)
        assertThat(cpu.d).isEqualTo(0x99)
        assertThat(cpu.e).isEqualTo(0xAA)
        assertThat(cpu.h).isEqualTo(0xBB)
        assertThat(cpu.l).isEqualTo(0xCC)
        assertThat(cpu.bAlt).isEqualTo(0x11)
        assertThat(cpu.cAlt).isEqualTo(0x22)
        assertThat(cpu.dAlt).isEqualTo(0x33)
        assertThat(cpu.eAlt).isEqualTo(0x44)
        assertThat(cpu.hAlt).isEqualTo(0x55)
        assertThat(cpu.lAlt).isEqualTo(0x66)
    }

    @Test
    fun `Exx does NOT touch a, f, aAlt, fAlt`() {
        val cpu =
            Cpu().apply {
                a = 0xAA
                f = 0xBB
                aAlt = 0xCC
                fAlt = 0xDD
            }
        Exx.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xAA)
        assertThat(cpu.f).isEqualTo(0xBB)
        assertThat(cpu.aAlt).isEqualTo(0xCC)
        assertThat(cpu.fAlt).isEqualTo(0xDD)
    }

    @Test
    fun `Exx advances pc, increments r, adds 4 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x500
                r = 5
                tStates = 0L
            }
        Exx.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x501)
        assertThat(cpu.r).isEqualTo(6)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Exx.mnemonic { 0 }).isEqualTo("EXX")
    }
}
