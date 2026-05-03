package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class JrRelCcTest {
    @Test
    fun `JR NZ, e jumps when Z is clear, 12 T-states taken`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                f = 0
            }
        val mem =
            Memory().apply {
                write(0x100, 0x20)
                write(0x101, 0x05)
            }
        JrRelCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x107)
        assertThat(cpu.tStates).isEqualTo(12L)
    }

    @Test
    fun `JR NZ, e falls through when Z is set, 7 T-states not-taken`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                tStates = 0L
                f = Flags.Z
            }
        val mem =
            Memory().apply {
                write(0x100, 0x20)
                write(0x101, 0x05)
            }
        JrRelCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.tStates).isEqualTo(7L)
    }

    @Test
    fun `JR rejects PO, PE, P, M conditions (only NZ Z NC C valid)`() {
        assertThatThrownBy { JrRelCc(cond = Condition.PO) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { JrRelCc(cond = Condition.M) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `JR cc does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                f = Flags.Z or Flags.C
            }
        val mem = Memory().apply { write(0x101, 0x05) }
        JrRelCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(Flags.Z or Flags.C)
    }

    @Test
    fun `mnemonic`() {
        assertThat(JrRelCc(cond = Condition.NZ).mnemonic { 0 }).isEqualTo("JR NZ, e")
        assertThat(JrRelCc(cond = Condition.C).mnemonic { 0 }).isEqualTo("JR C, e")
    }

    @Test
    fun `operandLength=1, baseCycles=7 (not-taken cost, taken adds 5)`() {
        val op = JrRelCc(cond = Condition.NZ)
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(7)
    }
}
