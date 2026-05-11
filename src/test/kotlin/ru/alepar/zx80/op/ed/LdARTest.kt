package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class LdARTest {
    @Test
    fun `LD A, R copies R to A and computes flags using IFF2 for PV`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0x42
                tStates = 0L
                a = 0
                iff2 = true
                f = Flags.C
            }
        LdAR.execute(cpu, Memory())
        // After bumpR(2): r = 0x42 + 2 = 0x44 (no bit-7 wrap), A gets post-bump value
        assertThat(cpu.a).isEqualTo(0x44)
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.tStates).isEqualTo(9L)
    }

    @Test
    fun `LD A, R does not set PV when iff2 is false`() {
        val cpu =
            Cpu().apply {
                r = 0x42
                iff2 = false
            }
        LdAR.execute(cpu, Memory())
        assertThat(cpu.f and Flags.PV).isZero
    }

    @Test
    fun `LD A, R copies the post-bump R value (bumped twice for ED-prefix opcode)`() {
        val cpu =
            Cpu().apply {
                a = 0
                r = 0x10
                pc = 0x100
                tStates = 0L
            }
        LdAR.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x12)
        assertThat(cpu.r).isEqualTo(0x12)
    }

    @Test
    fun `LD A, R preserves R bit 7 across bumps`() {
        // bumpR keeps bit 7 and wraps the low 7 bits mod 128. Starting R = 0xFE:
        // first bump: low 7 bits = 0x7E+1 = 0x7F, r = 0xFF
        // second bump: low 7 bits = 0x7F+1 = 0x00 (wraps), r = 0x80
        val cpu = Cpu().apply { r = 0xFE }
        LdAR.execute(cpu, Memory())
        assertThat(cpu.r).isEqualTo(0x80)
        assertThat(cpu.a).isEqualTo(0x80)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdAR.mnemonic { 0 }).isEqualTo("LD A, R")
    }

    @Test
    fun `LD A, R sets X and Y from result A bits 5 and 3`() {
        val cpu = Cpu().apply { r = 0x28 }
        LdAR.execute(cpu, Memory())
        // A is loaded from r before bumpR(2). Verify X/Y in F match A's X/Y bits
        assertThat(cpu.f and Flags.X).isEqualTo(cpu.a and Flags.X)
        assertThat(cpu.f and Flags.Y).isEqualTo(cpu.a and Flags.Y)
    }

    @Test
    fun `LD A, R X and Y match the byte loaded into A`() {
        val cpu = Cpu().apply { r = 0x07 }
        LdAR.execute(cpu, Memory())
        val expected = cpu.a and 0x28
        assertThat(cpu.f and 0x28).isEqualTo(expected)
    }
}
