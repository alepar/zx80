package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class CallAbsCcTest {
    @Test
    fun `CALL Z, nn taken pushes return addr and jumps, 17 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0x4000
                f = Flags.Z
            }
        val mem =
            Memory().apply {
                write(0x100, 0xCC)
                write(0x101, 0x00)
                write(0x102, 0x80)
            }
        CallAbsCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x8000)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x03)
        assertThat(cpu.tStates).isEqualTo(17L)
    }

    @Test
    fun `CALL Z, nn not-taken falls through, no push, 10 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                tStates = 0L
                sp = 0x4000
                f = 0
            }
        val mem =
            Memory().apply {
                write(0x100, 0xCC)
                write(0x101, 0x00)
                write(0x102, 0x80)
            }
        CallAbsCc(cond = Condition.Z).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.sp).isEqualTo(0x4000)
        assertThat(cpu.tStates).isEqualTo(10L)
    }

    @Test
    fun `CALL cc does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                sp = 0x4000
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x101, 0x00)
                write(0x102, 0x80)
            }
        CallAbsCc(cond = Condition.NZ).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(CallAbsCc(cond = Condition.NZ).mnemonic { 0 }).isEqualTo("CALL NZ, nn")
        assertThat(CallAbsCc(cond = Condition.M).mnemonic { 0 }).isEqualTo("CALL M, nn")
    }

    @Test
    fun `operandLength=2, baseCycles=10 (not-taken cost, taken adds 7)`() {
        val op = CallAbsCc(cond = Condition.NZ)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
