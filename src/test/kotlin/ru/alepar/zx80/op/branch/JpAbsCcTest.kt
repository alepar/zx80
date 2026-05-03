package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class JpAbsCcTest {
    @Test
    fun `JP NZ, nn jumps when Z is clear`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                f = 0
            }
        val mem =
            Memory().apply {
                write(0x100, 0xC2)
                write(0x101, 0x00)
                write(0x102, 0x80)
            }
        JpAbsCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x8000)
        assertThat(cpu.tStates).isEqualTo(10L) // same cost regardless
    }

    @Test
    fun `JP NZ, nn falls through when Z is set, advancing pc by 3`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                tStates = 0L
                f = Flags.Z
            }
        val mem =
            Memory().apply {
                write(0x100, 0xC2)
                write(0x101, 0x00)
                write(0x102, 0x80)
            }
        JpAbsCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x103) // not jumped
        assertThat(cpu.tStates).isEqualTo(10L) // still 10 (JP cc cost is constant)
    }

    @Test
    fun `JP Z, nn jumps when Z is set`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                f = Flags.Z
            }
        val mem =
            Memory().apply {
                write(0x101, 0x00)
                write(0x102, 0x40)
            }
        JpAbsCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x4000)
    }

    @Test
    fun `JP cc does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x101, 0x00)
                write(0x102, 0x80)
            }
        JpAbsCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JpAbsCc(cond = Condition.NZ).mnemonic { 0 }).isEqualTo("JP NZ, nn")
        assertThat(JpAbsCc(cond = Condition.M).mnemonic { 0 }).isEqualTo("JP M, nn")
    }

    @Test
    fun `operandLength=2, baseCycles=10`() {
        val op = JpAbsCc(cond = Condition.NZ)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
