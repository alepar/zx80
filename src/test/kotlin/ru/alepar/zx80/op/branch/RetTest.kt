package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class RetTest {
    @Test
    fun `RET pops pc from stack, increments SP, 10 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0x3FFE
            }
        val mem =
            Memory().apply {
                write(0x3FFE, 0x34)
                write(0x3FFF, 0x12)
            }
        Ret.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x1234)
        assertThat(cpu.sp).isEqualTo(0x4000)
        assertThat(cpu.tStates).isEqualTo(10L)
    }

    @Test
    fun `RET does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                sp = 0x3FFE
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x3FFE, 0x00)
                write(0x3FFF, 0x40)
            }
        Ret.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ret.mnemonic { 0 }).isEqualTo("RET")
    }

    @Test
    fun `operandLength=0, baseCycles=10`() {
        assertThat(Ret.operandLength).isZero
        assertThat(Ret.baseCycles).isEqualTo(10)
    }
}
