package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class SetRegTest {
    @Test
    fun `SET 7 B sets bit 7 of B, advances pc by 2, r by 2, 8 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x00
                f = 0xFF
            }
        SetReg(n = 7, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x80)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `SET 0 A on 0xFE gives 0xFF`() {
        val cpu = Cpu().apply { a = 0xFE }
        SetReg(n = 0, dst = Reg.A).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xFF)
    }

    @Test
    fun `SET preserves register value when bit was already set`() {
        val cpu = Cpu().apply { b = 0x80 }
        SetReg(n = 7, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x80)
    }

    @Test
    fun `SetReg rejects n outside 0 to 7`() {
        assertThatThrownBy { SetReg(n = 8, dst = Reg.B) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(SetReg(n = 0, dst = Reg.B).mnemonic { 0 }).isEqualTo("SET 0, B")
        assertThat(SetReg(n = 7, dst = Reg.A).mnemonic { 0 }).isEqualTo("SET 7, A")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = SetReg(n = 0, dst = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
