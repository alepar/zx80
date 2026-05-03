package ru.alepar.zx80.op.cb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.op.bit.BitHl
import ru.alepar.zx80.op.bit.BitReg
import ru.alepar.zx80.op.bit.ResHl
import ru.alepar.zx80.op.bit.ResReg
import ru.alepar.zx80.op.bit.SetHl
import ru.alepar.zx80.op.bit.SetReg
import ru.alepar.zx80.op.rot.RotShiftHl
import ru.alepar.zx80.op.rot.RotShiftReg

class CbOpsTest {
    @Test
    fun `installInto registers rotate-shift block in cb 0x00 to 0x3F skipping SLL at 0x30 to 0x37`() {
        val d = Decoder()
        CbOps.installInto(d)

        assertThat((d.cb[0x00] as RotShiftReg).mnemonic { 0 }).isEqualTo("RLC B")
        assertThat((d.cb[0x06] as RotShiftHl).mnemonic { 0 }).isEqualTo("RLC (HL)")
        assertThat((d.cb[0x07] as RotShiftReg).mnemonic { 0 }).isEqualTo("RLC A")
        assertThat((d.cb[0x08] as RotShiftReg).mnemonic { 0 }).isEqualTo("RRC B")
        assertThat((d.cb[0x20] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLA B")
        assertThat((d.cb[0x2D] as RotShiftReg).mnemonic { 0 }).isEqualTo("SRA L")
        assertThat((d.cb[0x38] as RotShiftReg).mnemonic { 0 }).isEqualTo("SRL B")
        assertThat((d.cb[0x3E] as RotShiftHl).mnemonic { 0 }).isEqualTo("SRL (HL)")
    }

    @Test
    fun `installInto registers SLL ops at CB 0x30 to 0x37`() {
        val d = Decoder()
        CbOps.installInto(d)
        // CB 0x30 = SLL B, CB 0x31 = SLL C, ..., CB 0x35 = SLL L,
        // CB 0x36 = SLL (HL), CB 0x37 = SLL A
        assertThat((d.cb[0x30] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL B")
        assertThat((d.cb[0x31] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL C")
        assertThat((d.cb[0x32] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL D")
        assertThat((d.cb[0x33] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL E")
        assertThat((d.cb[0x34] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL H")
        assertThat((d.cb[0x35] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL L")
        assertThat((d.cb[0x36] as RotShiftHl).mnemonic { 0 }).isEqualTo("SLL (HL)")
        assertThat((d.cb[0x37] as RotShiftReg).mnemonic { 0 }).isEqualTo("SLL A")
    }

    @Test
    fun `installInto registers all 64 rotate-shift opcodes in CB 0x00 to 0x3F`() {
        val d = Decoder()
        CbOps.installInto(d)
        val count = (0x00..0x3F).count { d.cb[it] != null }
        assertThat(count).isEqualTo(64)
    }

    @Test
    fun `installInto registers BIT n,r at CB 0x40 to 0x7F (64 opcodes)`() {
        val d = Decoder()
        CbOps.installInto(d)
        assertThat((d.cb[0x40] as BitReg).mnemonic { 0 }).isEqualTo("BIT 0, B")
        assertThat((d.cb[0x46] as BitHl).mnemonic { 0 }).isEqualTo("BIT 0, (HL)")
        assertThat((d.cb[0x47] as BitReg).mnemonic { 0 }).isEqualTo("BIT 0, A")
        assertThat((d.cb[0x78] as BitReg).mnemonic { 0 }).isEqualTo("BIT 7, B")
        assertThat((d.cb[0x7E] as BitHl).mnemonic { 0 }).isEqualTo("BIT 7, (HL)")
        assertThat((d.cb[0x7F] as BitReg).mnemonic { 0 }).isEqualTo("BIT 7, A")

        val count = (0x40..0x7F).count { d.cb[it] != null }
        assertThat(count).isEqualTo(64)
    }

    @Test
    fun `installInto registers RES n,r at CB 0x80 to 0xBF (64 opcodes)`() {
        val d = Decoder()
        CbOps.installInto(d)
        assertThat((d.cb[0x80] as ResReg).mnemonic { 0 }).isEqualTo("RES 0, B")
        assertThat((d.cb[0x86] as ResHl).mnemonic { 0 }).isEqualTo("RES 0, (HL)")
        assertThat((d.cb[0x87] as ResReg).mnemonic { 0 }).isEqualTo("RES 0, A")
        assertThat((d.cb[0xB8] as ResReg).mnemonic { 0 }).isEqualTo("RES 7, B")
        assertThat((d.cb[0xBE] as ResHl).mnemonic { 0 }).isEqualTo("RES 7, (HL)")
        assertThat((d.cb[0xBF] as ResReg).mnemonic { 0 }).isEqualTo("RES 7, A")

        val count = (0x80..0xBF).count { d.cb[it] != null }
        assertThat(count).isEqualTo(64)
    }

    @Test
    fun `installInto registers SET n,r at CB 0xC0 to 0xFF (64 opcodes)`() {
        val d = Decoder()
        CbOps.installInto(d)
        assertThat((d.cb[0xC0] as SetReg).mnemonic { 0 }).isEqualTo("SET 0, B")
        assertThat((d.cb[0xC6] as SetHl).mnemonic { 0 }).isEqualTo("SET 0, (HL)")
        assertThat((d.cb[0xC7] as SetReg).mnemonic { 0 }).isEqualTo("SET 0, A")
        assertThat((d.cb[0xF8] as SetReg).mnemonic { 0 }).isEqualTo("SET 7, B")
        assertThat((d.cb[0xFE] as SetHl).mnemonic { 0 }).isEqualTo("SET 7, (HL)")
        assertThat((d.cb[0xFF] as SetReg).mnemonic { 0 }).isEqualTo("SET 7, A")

        val count = (0xC0..0xFF).count { d.cb[it] != null }
        assertThat(count).isEqualTo(64)
    }

    @Test
    fun `installInto fills all 256 CB opcodes (with SLL added in Phase 2_12)`() {
        val d = Decoder()
        CbOps.installInto(d)
        val total = d.cb.count { it != null }
        assertThat(total).isEqualTo(256)
    }
}
