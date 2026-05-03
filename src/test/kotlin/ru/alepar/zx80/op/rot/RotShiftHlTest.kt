package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RotShiftHlTest {
    @Test
    fun `RLC (HL) reads and writes byte at HL, 15 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                hl = 0x4000
                f = 0
            }
        val mem = Memory().apply { write(0x4000, 0x80) }
        RotShiftHl(op = RotateOp.RLC).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x01)
        assertThat(cpu.hl).isEqualTo(0x4000)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(15L)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(RotShiftHl(op = RotateOp.RLC).mnemonic { 0 }).isEqualTo("RLC (HL)")
        assertThat(RotShiftHl(op = RotateOp.SRL).mnemonic { 0 }).isEqualTo("SRL (HL)")
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = RotShiftHl(op = RotateOp.RLC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }

    @Test
    fun `SLL (HL) shifts memory at HL, sets bit 0 to 1`() {
        val cpu = Cpu().apply { hl = 0x4000 }
        val mem = Memory().apply { write(0x4000, 0x80) }
        RotShiftHl(RotateOp.SLL).execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x01)
        assertThat(cpu.f and Flags.C).isNotZero
    }
}
