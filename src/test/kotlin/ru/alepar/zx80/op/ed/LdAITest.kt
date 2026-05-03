package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class LdAITest {
    @Test
    fun `LD A, I copies I to A and computes flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                i = 0x42
                a = 0
                iff2 = false
                f = Flags.C
            }
        LdAI.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f and Flags.S).isZero
        assertThat(cpu.f and Flags.Z).isZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.PV).isZero
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.tStates).isEqualTo(9L)
    }

    @Test
    fun `LD A, I sets PV when iff2 is true`() {
        val cpu =
            Cpu().apply {
                i = 0x42
                iff2 = true
            }
        LdAI.execute(cpu, Memory())
        assertThat(cpu.f and Flags.PV).isNotZero
    }

    @Test
    fun `LD A, I sets Z when I is zero`() {
        val cpu =
            Cpu().apply {
                i = 0
                iff2 = false
            }
        LdAI.execute(cpu, Memory())
        assertThat(cpu.a).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `LD A, I sets S when I bit 7 is set`() {
        val cpu =
            Cpu().apply {
                i = 0x80
                iff2 = false
            }
        LdAI.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x80)
        assertThat(cpu.f and Flags.S).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAI.mnemonic { 0 }).isEqualTo("LD A, I")
    }

    @Test
    fun `operandLength=0, baseCycles=9`() {
        assertThat(LdAI.operandLength).isZero
        assertThat(LdAI.baseCycles).isEqualTo(9)
    }

    @Test
    fun `LD A, I sets X and Y from result A bits 5 and 3`() {
        val cpu = Cpu().apply { i = 0x28 }
        LdAI.execute(cpu, Memory())
        // A = 0x28 -> X+Y both set
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isNotZero

        val cpu2 = Cpu().apply { i = 0x20 }
        LdAI.execute(cpu2, Memory())
        // A = 0x20 -> only X
        assertThat(cpu2.f and Flags.X).isNotZero
        assertThat(cpu2.f and Flags.Y).isZero

        val cpu3 = Cpu().apply { i = 0x08 }
        LdAI.execute(cpu3, Memory())
        // A = 0x08 -> only Y
        assertThat(cpu3.f and Flags.X).isZero
        assertThat(cpu3.f and Flags.Y).isNotZero
    }
}
