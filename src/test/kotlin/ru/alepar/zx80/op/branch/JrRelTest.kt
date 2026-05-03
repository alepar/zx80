package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class JrRelTest {
    @Test
    fun `JR e with positive displacement jumps forward`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
            }
        val mem =
            Memory().apply {
                write(0x100, 0x18)
                write(0x101, 0x05) // displacement = +5
            }
        JrRel.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x107) // 0x100 + 2 + 5
        assertThat(cpu.tStates).isEqualTo(12L)
    }

    @Test
    fun `JR e with negative displacement jumps backward`() {
        val cpu = Cpu().apply { pc = 0x100 }
        val mem =
            Memory().apply {
                write(0x100, 0x18)
                write(0x101, 0xFE) // displacement = -2 (signed)
            }
        JrRel.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x100)
    }

    @Test
    fun `JR e wraps PC mod 64K`() {
        val cpu = Cpu().apply { pc = 0xFFFE }
        val mem =
            Memory().apply {
                write(0xFFFE, 0x18)
                write(0xFFFF, 0x05)
            }
        JrRel.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x0005)
    }

    @Test
    fun `JR e does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                f = 0xFF
            }
        val mem = Memory().apply { write(0x101, 0x05) }
        JrRel.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JrRel.mnemonic { 0 }).isEqualTo("JR e")
    }

    @Test
    fun `operandLength=1, baseCycles=12`() {
        assertThat(JrRel.operandLength).isEqualTo(1)
        assertThat(JrRel.baseCycles).isEqualTo(12)
    }
}
