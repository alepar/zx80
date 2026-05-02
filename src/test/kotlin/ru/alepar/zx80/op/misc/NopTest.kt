package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class NopTest {
    @Test
    fun `Nop advances pc by 1, increments r by 1, adds 4 T-states, leaves all else untouched`() {
        val cpu =
            Cpu().apply {
                pc = 0x1000
                r = 0x10
                tStates = 100L
                a = 0x55
                f = 0xAA
                b = 0x11
                bc = 0x1122
            }
        Nop.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x1001)
        assertThat(cpu.r).isEqualTo(0x11)
        assertThat(cpu.tStates).isEqualTo(104L)
        assertThat(cpu.a).isEqualTo(0x55)
        assertThat(cpu.f).isEqualTo(0xAA)
        assertThat(cpu.bc).isEqualTo(0x1122)
    }

    @Test
    fun `Nop r increment wraps within bottom 7 bits, top bit preserved`() {
        val cpu = Cpu().apply { r = 0xFF } // bottom 7 bits = 0x7F, top bit = 1
        Nop.execute(cpu, Memory())
        // Bottom 7 bits go 0x7F → 0x00; top bit stays at 1; result = 0x80
        assertThat(cpu.r).isEqualTo(0x80)
    }

    @Test
    fun `Nop pc wrap mod 64K`() {
        val cpu = Cpu().apply { pc = 0xFFFF }
        Nop.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x0000)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Nop.mnemonic { 0 }).isEqualTo("NOP")
    }
}
