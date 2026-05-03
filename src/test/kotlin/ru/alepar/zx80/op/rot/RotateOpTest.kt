package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Flags

class RotateOpTest {
    @Test
    fun `apply RLC delegates to Flags afterRlc`() {
        val r = RotateOp.RLC.apply(0x80, oldF = 0)
        assertThat(r.value).isEqualTo(0x01)
    }

    @Test
    fun `apply RR folds in oldF carry`() {
        val r = RotateOp.RR.apply(0x01, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x80)
    }

    @Test
    fun `apply SRA preserves sign`() {
        val r = RotateOp.SRA.apply(0x80, oldF = 0)
        assertThat(r.value).isEqualTo(0xC0)
    }

    @Test
    fun `apply SRL clears top bit`() {
        val r = RotateOp.SRL.apply(0x80, oldF = 0)
        assertThat(r.value).isEqualTo(0x40)
    }

    @Test
    fun `mnemonic matches op name`() {
        assertThat(RotateOp.RLC.mnemonic).isEqualTo("RLC")
        assertThat(RotateOp.SRL.mnemonic).isEqualTo("SRL")
    }

    @Test
    fun `fromBits maps 0 to 7 (including SLL at 6)`() {
        assertThat(RotateOp.fromBits(0)).isEqualTo(RotateOp.RLC)
        assertThat(RotateOp.fromBits(1)).isEqualTo(RotateOp.RRC)
        assertThat(RotateOp.fromBits(2)).isEqualTo(RotateOp.RL)
        assertThat(RotateOp.fromBits(3)).isEqualTo(RotateOp.RR)
        assertThat(RotateOp.fromBits(4)).isEqualTo(RotateOp.SLA)
        assertThat(RotateOp.fromBits(5)).isEqualTo(RotateOp.SRA)
        assertThat(RotateOp.fromBits(6)).isEqualTo(RotateOp.SLL)
        assertThat(RotateOp.fromBits(7)).isEqualTo(RotateOp.SRL)
    }
}
