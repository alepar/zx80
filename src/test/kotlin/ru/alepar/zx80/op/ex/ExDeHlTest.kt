package ru.alepar.zx80.op.ex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ExDeHlTest {
    @Test
    fun `ExDeHl swaps de with hl`() {
        val cpu =
            Cpu().apply {
                d = 0x11
                e = 0x22
                h = 0x33
                l = 0x44
            }
        ExDeHl.execute(cpu, Memory())
        assertThat(cpu.d).isEqualTo(0x33)
        assertThat(cpu.e).isEqualTo(0x44)
        assertThat(cpu.h).isEqualTo(0x11)
        assertThat(cpu.l).isEqualTo(0x22)
    }

    @Test
    fun `ExDeHl does NOT touch alternate registers`() {
        val cpu =
            Cpu().apply {
                dAlt = 0xAA
                eAlt = 0xBB
                hAlt = 0xCC
                lAlt = 0xDD
            }
        ExDeHl.execute(cpu, Memory())
        assertThat(cpu.dAlt).isEqualTo(0xAA)
        assertThat(cpu.eAlt).isEqualTo(0xBB)
        assertThat(cpu.hAlt).isEqualTo(0xCC)
        assertThat(cpu.lAlt).isEqualTo(0xDD)
    }

    @Test
    fun `ExDeHl advances pc, increments r, adds 4 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x500
                r = 5
                tStates = 0L
            }
        ExDeHl.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x501)
        assertThat(cpu.r).isEqualTo(6)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(ExDeHl.mnemonic { 0 }).isEqualTo("EX DE, HL")
    }
}
