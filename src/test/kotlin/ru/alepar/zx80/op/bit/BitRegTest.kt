package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class BitRegTest {
    @Test
    fun `BIT 7 B sets Z when bit 7 of B is clear`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x7F
                f = 0
            }
        BitReg(n = 7, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.b).isEqualTo(0x7F)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `BIT 7 B clears Z when bit 7 of B is set`() {
        val cpu =
            Cpu().apply {
                b = 0x80
                f = Flags.Z
            }
        BitReg(n = 7, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.f and Flags.Z).isZero
    }

    @Test
    fun `BIT 0 A on 0x01`() {
        val cpu = Cpu().apply { a = 0x01 }
        BitReg(n = 0, src = Reg.A).execute(cpu, Memory())
        assertThat(cpu.f and Flags.Z).isZero
    }

    @Test
    fun `BIT preserves C flag`() {
        val cpu =
            Cpu().apply {
                b = 0x80
                f = Flags.C
            }
        BitReg(n = 7, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `BitReg rejects n outside 0 to 7`() {
        assertThatThrownBy { BitReg(n = 8, src = Reg.B) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { BitReg(n = -1, src = Reg.B) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(BitReg(n = 0, src = Reg.B).mnemonic { 0 }).isEqualTo("BIT 0, B")
        assertThat(BitReg(n = 7, src = Reg.A).mnemonic { 0 }).isEqualTo("BIT 7, A")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = BitReg(n = 0, src = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
