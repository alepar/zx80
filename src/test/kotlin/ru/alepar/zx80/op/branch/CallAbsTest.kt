package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class CallAbsTest {
    @Test
    fun `CALL nn pushes pc+3 and jumps, 17 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0x4000
            }
        val mem =
            Memory().apply {
                write(0x100, 0xCD)
                write(0x101, 0x00)
                write(0x102, 0x80)
            }
        CallAbs.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x8000)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x03)
        assertThat(mem.read(0x3FFF)).isEqualTo(0x01)
        assertThat(cpu.tStates).isEqualTo(17L)
    }

    @Test
    fun `CALL does NOT touch flags`() {
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
        CallAbs.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(CallAbs.mnemonic { 0 }).isEqualTo("CALL nn")
    }

    @Test
    fun `operandLength=2, baseCycles=17`() {
        assertThat(CallAbs.operandLength).isEqualTo(2)
        assertThat(CallAbs.baseCycles).isEqualTo(17)
    }
}
