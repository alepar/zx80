package ru.alepar.zx80.op.ex

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ExSpHlTest {
    @Test
    fun `ExSpHl swaps L with byte at SP and H with byte at SP+1`() {
        val cpu =
            Cpu().apply {
                sp = 0x4000
                h = 0xAB
                l = 0xCD
            }
        val mem =
            Memory().apply {
                write(0x4000, 0x12) // (SP) — will become L
                write(0x4001, 0x34) // (SP+1) — will become H
            }
        ExSpHl.execute(cpu, mem)
        assertThat(cpu.l).isEqualTo(0x12)
        assertThat(cpu.h).isEqualTo(0x34)
        assertThat(mem.read(0x4000)).isEqualTo(0xCD) // old L
        assertThat(mem.read(0x4001)).isEqualTo(0xAB) // old H
    }

    @Test
    fun `ExSpHl does NOT change SP itself`() {
        val cpu = Cpu().apply { sp = 0x4000 }
        ExSpHl.execute(cpu, Memory())
        assertThat(cpu.sp).isEqualTo(0x4000)
    }

    @Test
    fun `ExSpHl does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                sp = 0x4000
                f = 0xAA
            }
        ExSpHl.execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `ExSpHl advances pc, increments r, adds 19 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x500
                r = 5
                tStates = 0L
                sp = 0x4000
            }
        ExSpHl.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x501)
        assertThat(cpu.r).isEqualTo(6)
        assertThat(cpu.tStates).isEqualTo(19L)
    }

    @Test
    fun `ExSpHl wraps SP+1 mod 64K`() {
        val cpu =
            Cpu().apply {
                sp = 0xFFFF
                h = 0x11
                l = 0x22
            }
        val mem =
            Memory().apply {
                write(0xFFFF, 0x33)
                write(0x0000, 0x44)
            }
        ExSpHl.execute(cpu, mem)
        assertThat(cpu.l).isEqualTo(0x33)
        assertThat(cpu.h).isEqualTo(0x44)
        assertThat(mem.read(0xFFFF)).isEqualTo(0x22)
        assertThat(mem.read(0x0000)).isEqualTo(0x11)
    }

    @Test
    fun `mnemonic`() {
        assertThat(ExSpHl.mnemonic { 0 }).isEqualTo("EX (SP), HL")
    }
}
