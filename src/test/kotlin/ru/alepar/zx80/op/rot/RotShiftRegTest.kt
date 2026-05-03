package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class RotShiftRegTest {
    @Test
    fun `RLC B updates B and F, advances pc by 2, r by 2, 8 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x80
                f = 0
            }
        RotShiftReg(op = RotateOp.RLC, src = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x01)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `RR A folds in carry`() {
        val cpu =
            Cpu().apply {
                a = 0x01
                f = Flags.C
            }
        RotShiftReg(op = RotateOp.RR, src = Reg.A).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x80)
    }

    @Test
    fun `SRA L preserves sign bit`() {
        val cpu = Cpu().apply { l = 0x80 }
        RotShiftReg(op = RotateOp.SRA, src = Reg.L).execute(cpu, Memory())
        assertThat(cpu.l).isEqualTo(0xC0)
    }

    @Test
    fun `SRL H clears top bit`() {
        val cpu = Cpu().apply { h = 0x80 }
        RotShiftReg(op = RotateOp.SRL, src = Reg.H).execute(cpu, Memory())
        assertThat(cpu.h).isEqualTo(0x40)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(RotShiftReg(op = RotateOp.RLC, src = Reg.B).mnemonic { 0 }).isEqualTo("RLC B")
        assertThat(RotShiftReg(op = RotateOp.SRL, src = Reg.A).mnemonic { 0 }).isEqualTo("SRL A")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = RotShiftReg(op = RotateOp.RLC, src = Reg.B)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }

    @Test
    fun `SLL B shifts left, sets bit 0 to 1`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x55
            }
        RotShiftReg(RotateOp.SLL, Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0xAB)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `SLL mnemonic is SLL r`() {
        assertThat(RotShiftReg(RotateOp.SLL, Reg.B).mnemonic { 0 }).isEqualTo("SLL B")
    }
}
