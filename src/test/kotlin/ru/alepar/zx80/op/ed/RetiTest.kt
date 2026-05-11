package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class RetiTest {
    @Test
    fun `RETI pops PC from stack, no flag changes, 14T, R+=2`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                sp = 0x1000
                r = 0
                tStates = 0L
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x1000, 0x34)
                write(0x1001, 0x12)
            }
        Reti.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x1234)
        assertThat(cpu.sp).isEqualTo(0x1002)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(14L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `RETI restores iff1 from iff2`() {
        val cpu =
            Cpu().apply {
                sp = 0xFFFE
                iff1 = false
                iff2 = true
            }
        val mem =
            Memory().apply {
                write(0xFFFE, 0x34) // return address low
                write(0xFFFF, 0x12) // return address high
            }
        Reti.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x1234)
        assertThat(cpu.iff1).isTrue
        assertThat(cpu.iff2).isTrue
    }

    @Test
    fun `mnemonic`() {
        assertThat(Reti.mnemonic { 0 }).isEqualTo("RETI")
    }
}
