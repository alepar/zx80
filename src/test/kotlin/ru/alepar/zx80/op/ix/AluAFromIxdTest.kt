package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.alu.AluOp

class AluAFromIxdTest {
    @Test
    fun `ADD A, (IX+5) adds byte at IX+5 to A, 19 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                a = 0x05
            }
        val mem =
            Memory().apply {
                write(0x100, 0xDD)
                write(0x101, 0x86)
                write(0x102, 0x05)
                write(0x4005, 0x03)
            }
        AluAFromIxd(idx = IndexReg.IX, op = AluOp.ADD).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x08)
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(19L)
    }

    @Test
    fun `CP A, (IY-1) does NOT update A`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0x4000
                a = 0x42
            }
        val mem =
            Memory().apply {
                write(0x102, 0xFF)
                write(0x3FFF, 0x42)
            }
        AluAFromIxd(idx = IndexReg.IY, op = AluOp.CP).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(AluAFromIxd(idx = IndexReg.IX, op = AluOp.ADD).mnemonic { 0 })
            .isEqualTo("ADD A, (IX+d)")
        assertThat(AluAFromIxd(idx = IndexReg.IY, op = AluOp.CP).mnemonic { 0 })
            .isEqualTo("CP A, (IY+d)")
    }

    @Test
    fun `operandLength=1, baseCycles=19`() {
        val op = AluAFromIxd(idx = IndexReg.IX, op = AluOp.ADD)
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(19)
    }
}
