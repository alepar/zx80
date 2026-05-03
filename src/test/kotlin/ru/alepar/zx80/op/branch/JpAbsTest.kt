package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class JpAbsTest {
    @Test
    fun `JP nn sets pc to little-endian word at pc+1, 10 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
            }
        val mem =
            Memory().apply {
                write(0x100, 0xC3) // JP nn opcode
                write(0x101, 0x00) // low byte of nn
                write(0x102, 0x80) // high byte of nn (nn = 0x8000)
            }
        JpAbs.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x8000)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(10L)
    }

    @Test
    fun `JP nn does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x101, 0x00)
                write(0x102, 0x40)
            }
        JpAbs.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JpAbs.mnemonic { 0 }).isEqualTo("JP nn")
    }

    @Test
    fun `operandLength=2, baseCycles=10`() {
        assertThat(JpAbs.operandLength).isEqualTo(2)
        assertThat(JpAbs.baseCycles).isEqualTo(10)
    }
}
