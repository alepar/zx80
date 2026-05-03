package ru.alepar.zx80.op.bit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class ResRegTest {
    @Test
    fun `RES 7 B clears bit 7 of B, advances pc by 2, r by 2, 8 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0xFF
                f = 0xFF
            }
        ResReg(n = 7, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x7F)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `RES 0 A clears bit 0 of A`() {
        val cpu = Cpu().apply { a = 0xFF }
        ResReg(n = 0, dst = Reg.A).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xFE)
    }

    @Test
    fun `RES 3 L on 0xFF gives 0xF7`() {
        val cpu = Cpu().apply { l = 0xFF }
        ResReg(n = 3, dst = Reg.L).execute(cpu, Memory())
        assertThat(cpu.l).isEqualTo(0xF7)
    }

    @Test
    fun `RES preserves the register value when bit was already clear`() {
        val cpu = Cpu().apply { b = 0x7F }
        ResReg(n = 7, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x7F)
    }

    @Test
    fun `ResReg rejects n outside 0 to 7`() {
        assertThatThrownBy { ResReg(n = 8, dst = Reg.B) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(ResReg(n = 0, dst = Reg.B).mnemonic { 0 }).isEqualTo("RES 0, B")
        assertThat(ResReg(n = 7, dst = Reg.A).mnemonic { 0 }).isEqualTo("RES 7, A")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = ResReg(n = 0, dst = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
