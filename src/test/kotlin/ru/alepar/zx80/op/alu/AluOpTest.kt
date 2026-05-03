package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Flags

class AluOpTest {
    @Test
    fun `apply ADD computes sum`() {
        val r = AluOp.ADD.apply(a = 0x05, b = 0x03, oldF = 0)
        assertThat(r.value).isEqualTo(0x08)
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `apply ADC folds incoming C bit`() {
        val r = AluOp.ADC.apply(a = 0x05, b = 0x03, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x09)
    }

    @Test
    fun `apply SUB computes difference and sets N`() {
        val r = AluOp.SUB.apply(a = 0x05, b = 0x02, oldF = 0)
        assertThat(r.value).isEqualTo(0x03)
        assertThat(r.newF and Flags.N).isNotZero
    }

    @Test
    fun `apply SBC folds incoming C bit as borrow`() {
        val r = AluOp.SBC.apply(a = 0x05, b = 0x02, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x02)
    }

    @Test
    fun `apply CP computes flags like SUB`() {
        val r = AluOp.CP.apply(a = 0x05, b = 0x05, oldF = 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.N).isNotZero
    }

    @Test
    fun `apply AND computes AND`() {
        val r = AluOp.AND.apply(a = 0xFF, b = 0x0F, oldF = 0)
        assertThat(r.value).isEqualTo(0x0F)
        assertThat(r.newF and Flags.H).isNotZero
    }

    @Test
    fun `apply OR computes OR`() {
        val r = AluOp.OR.apply(a = 0x0F, b = 0xF0, oldF = 0)
        assertThat(r.value).isEqualTo(0xFF)
    }

    @Test
    fun `apply XOR computes XOR`() {
        val r = AluOp.XOR.apply(a = 0xFF, b = 0x0F, oldF = 0)
        assertThat(r.value).isEqualTo(0xF0)
    }

    @Test
    fun `updatesA is true for all except CP`() {
        assertThat(AluOp.ADD.updatesA).isTrue
        assertThat(AluOp.ADC.updatesA).isTrue
        assertThat(AluOp.SUB.updatesA).isTrue
        assertThat(AluOp.SBC.updatesA).isTrue
        assertThat(AluOp.AND.updatesA).isTrue
        assertThat(AluOp.OR.updatesA).isTrue
        assertThat(AluOp.XOR.updatesA).isTrue
        assertThat(AluOp.CP.updatesA).isFalse
    }

    @Test
    fun `mnemonic matches enum name`() {
        assertThat(AluOp.ADD.mnemonic).isEqualTo("ADD")
        assertThat(AluOp.CP.mnemonic).isEqualTo("CP")
    }

    @Test
    fun `fromBits maps Z80 ALU encoding`() {
        assertThat(AluOp.fromBits(0)).isEqualTo(AluOp.ADD)
        assertThat(AluOp.fromBits(1)).isEqualTo(AluOp.ADC)
        assertThat(AluOp.fromBits(2)).isEqualTo(AluOp.SUB)
        assertThat(AluOp.fromBits(3)).isEqualTo(AluOp.SBC)
        assertThat(AluOp.fromBits(4)).isEqualTo(AluOp.AND)
        assertThat(AluOp.fromBits(5)).isEqualTo(AluOp.XOR)
        assertThat(AluOp.fromBits(6)).isEqualTo(AluOp.OR)
        assertThat(AluOp.fromBits(7)).isEqualTo(AluOp.CP)
    }
}
