package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class DjnzTest {
    @Test
    fun `DJNZ decrements B, jumps if B is non-zero after, 13 T-states taken`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x05
            }
        val mem =
            Memory().apply {
                write(0x100, 0x10)
                write(0x101, 0xFE) // displacement -2
            }
        Djnz.execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x04)
        assertThat(cpu.pc).isEqualTo(0x100)
        assertThat(cpu.tStates).isEqualTo(13L)
    }

    @Test
    fun `DJNZ decrements B, falls through when B becomes 0, 8 T-states not-taken`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                tStates = 0L
                b = 0x01
            }
        val mem =
            Memory().apply {
                write(0x100, 0x10)
                write(0x101, 0x05)
            }
        Djnz.execute(cpu, mem)
        assertThat(cpu.b).isZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `DJNZ wraps B from 0x00 to 0xFF and JUMPS (since 0xFF is non-zero)`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                b = 0x00
            }
        val mem = Memory().apply { write(0x101, 0x10) }
        Djnz.execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0xFF)
        assertThat(cpu.pc).isEqualTo(0x112)
    }

    @Test
    fun `DJNZ does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                b = 0x05
                f = 0xFF
            }
        val mem = Memory().apply { write(0x101, 0x05) }
        Djnz.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Djnz.mnemonic { 0 }).isEqualTo("DJNZ e")
    }

    @Test
    fun `operandLength=1, baseCycles=8 (not-taken)`() {
        assertThat(Djnz.operandLength).isEqualTo(1)
        assertThat(Djnz.baseCycles).isEqualTo(8)
    }
}
