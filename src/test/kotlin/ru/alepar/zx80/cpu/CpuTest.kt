package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CpuTest {
    @Test
    fun `fresh Cpu has all registers zeroed`() {
        val cpu = Cpu()
        assertThat(cpu.a).isZero
        assertThat(cpu.f).isZero
        assertThat(cpu.b).isZero
        assertThat(cpu.c).isZero
        assertThat(cpu.d).isZero
        assertThat(cpu.e).isZero
        assertThat(cpu.h).isZero
        assertThat(cpu.l).isZero
        assertThat(cpu.aAlt).isZero
        assertThat(cpu.fAlt).isZero
        assertThat(cpu.bAlt).isZero
        assertThat(cpu.cAlt).isZero
        assertThat(cpu.dAlt).isZero
        assertThat(cpu.eAlt).isZero
        assertThat(cpu.hAlt).isZero
        assertThat(cpu.lAlt).isZero
        assertThat(cpu.ix).isZero
        assertThat(cpu.iy).isZero
        assertThat(cpu.sp).isZero
        assertThat(cpu.pc).isZero
        assertThat(cpu.i).isZero
        assertThat(cpu.r).isZero
        assertThat(cpu.iff1).isFalse
        assertThat(cpu.iff2).isFalse
        assertThat(cpu.im).isZero
        assertThat(cpu.halted).isFalse
        assertThat(cpu.tStates).isZero
    }

    @Test
    fun `register pair accessors compute from 8-bit halves`() {
        val cpu = Cpu()
        cpu.b = 0xAB
        cpu.c = 0xCD
        cpu.d = 0x12
        cpu.e = 0x34
        cpu.h = 0xDE
        cpu.l = 0xAD
        cpu.a = 0x55
        cpu.f = 0x66

        assertThat(cpu.bc).isEqualTo(0xABCD)
        assertThat(cpu.de).isEqualTo(0x1234)
        assertThat(cpu.hl).isEqualTo(0xDEAD)
        assertThat(cpu.af).isEqualTo(0x5566)
    }

    @Test
    fun `setting register pair updates 8-bit halves`() {
        val cpu = Cpu()
        cpu.bc = 0x1234
        assertThat(cpu.b).isEqualTo(0x12)
        assertThat(cpu.c).isEqualTo(0x34)

        cpu.de = 0xFFFF
        assertThat(cpu.d).isEqualTo(0xFF)
        assertThat(cpu.e).isEqualTo(0xFF)

        cpu.hl = 0x0001
        assertThat(cpu.h).isEqualTo(0x00)
        assertThat(cpu.l).isEqualTo(0x01)

        cpu.af = 0xCAFE
        assertThat(cpu.a).isEqualTo(0xCA)
        assertThat(cpu.f).isEqualTo(0xFE)
    }

    @Test
    fun `register pair setter masks to 16 bits`() {
        val cpu = Cpu()
        cpu.bc = 0x1_2345 // 17-bit value; top bit must be discarded
        assertThat(cpu.bc).isEqualTo(0x2345)
    }
}
