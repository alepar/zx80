package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.alu.AluOp

class AluAFromIxHalfTest {
    @Test
    fun `ADD A, IXH adds high byte of IX into A`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x1234
                a = 0x10
            }
        AluAFromIxHalf(op = AluOp.ADD, src = IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x10 + 0x12)
        assertThat(cpu.ix).isEqualTo(0x1234)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `SUB IYL subtracts low byte of IY from A`() {
        val cpu =
            Cpu().apply {
                iy = 0xAB05
                a = 0x10
            }
        AluAFromIxHalf(op = AluOp.SUB, src = IndexHalfReg.IYL).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x10 - 0x05)
    }

    @Test
    fun `CP IXH does not modify A`() {
        val cpu =
            Cpu().apply {
                ix = 0x4200
                a = 0x42
            }
        AluAFromIxHalf(op = AluOp.CP, src = IndexHalfReg.IXH).execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x42)
    }

    @Test
    fun `mnemonic format is OP A, half`() {
        assertThat(AluAFromIxHalf(AluOp.ADD, IndexHalfReg.IXH).mnemonic { 0 })
            .isEqualTo("ADD A, IXH")
        assertThat(AluAFromIxHalf(AluOp.XOR, IndexHalfReg.IYL).mnemonic { 0 })
            .isEqualTo("XOR A, IYL")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = AluAFromIxHalf(AluOp.ADD, IndexHalfReg.IXH)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
