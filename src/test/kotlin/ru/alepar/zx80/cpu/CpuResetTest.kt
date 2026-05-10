package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CpuResetTest {
    @Test
    fun `reset puts Cpu in Z80 power-on state`() {
        val cpu =
            Cpu().apply {
                // Dirty every field so reset must clear it
                a = 0x12
                f = 0x34
                b = 0x56
                c = 0x78
                d = 0x9A
                e = 0xBC
                h = 0xDE
                l = 0xF0
                aAlt = 0x01
                fAlt = 0x02
                bAlt = 0x03
                cAlt = 0x04
                dAlt = 0x05
                eAlt = 0x06
                hAlt = 0x07
                lAlt = 0x08
                ix = 0x1234
                iy = 0x5678
                sp = 0x0100
                pc = 0x0200
                i = 0x42
                r = 0x55
                memptr = 0x9999
                iff1 = true
                iff2 = true
                im = 2
                halted = true
                tStates = 12345L
            }

        cpu.reset()

        assertThat(cpu.pc).isEqualTo(0x0000)
        assertThat(cpu.sp).isEqualTo(0xFFFF)
        assertThat(cpu.af).isEqualTo(0xFFFF)
        assertThat(cpu.bc).isEqualTo(0xFFFF)
        assertThat(cpu.de).isEqualTo(0xFFFF)
        assertThat(cpu.hl).isEqualTo(0xFFFF)
        assertThat(cpu.aAlt).isEqualTo(0xFF)
        assertThat(cpu.fAlt).isEqualTo(0xFF)
        assertThat(cpu.bAlt).isEqualTo(0xFF)
        assertThat(cpu.cAlt).isEqualTo(0xFF)
        assertThat(cpu.dAlt).isEqualTo(0xFF)
        assertThat(cpu.eAlt).isEqualTo(0xFF)
        assertThat(cpu.hAlt).isEqualTo(0xFF)
        assertThat(cpu.lAlt).isEqualTo(0xFF)
        assertThat(cpu.ix).isEqualTo(0xFFFF)
        assertThat(cpu.iy).isEqualTo(0xFFFF)
        assertThat(cpu.i).isEqualTo(0x00)
        assertThat(cpu.r).isEqualTo(0x00)
        assertThat(cpu.memptr).isEqualTo(0x0000)
        assertThat(cpu.iff1).isFalse
        assertThat(cpu.iff2).isFalse
        assertThat(cpu.im).isEqualTo(0)
        assertThat(cpu.halted).isFalse
        assertThat(cpu.tStates).isEqualTo(0L)
    }

    @Test
    fun `reset is idempotent`() {
        val cpu = Cpu()
        cpu.reset()
        val pc1 = cpu.pc
        val sp1 = cpu.sp
        val af1 = cpu.af
        val r1 = cpu.r
        val tStates1 = cpu.tStates
        cpu.reset()
        assertThat(cpu.pc).isEqualTo(pc1)
        assertThat(cpu.sp).isEqualTo(sp1)
        assertThat(cpu.af).isEqualTo(af1)
        assertThat(cpu.r).isEqualTo(r1)
        assertThat(cpu.tStates).isEqualTo(tStates1)
    }

    @Test
    fun `bumpR after reset gives R=1`() {
        val cpu = Cpu().apply { r = 0x77 }
        cpu.reset()
        assertThat(cpu.r).isEqualTo(0)
        cpu.bumpR()
        assertThat(cpu.r).isEqualTo(1)
    }
}
