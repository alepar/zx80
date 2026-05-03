package ru.alepar.zx80.op.cb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.op.bit.BitHl
import ru.alepar.zx80.op.bit.BitReg
import ru.alepar.zx80.op.bit.ResHl
import ru.alepar.zx80.op.bit.ResReg
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
    fun `installInto leaves SLL slots (CB 0x30 to 0x37) null`() {
        val d = Decoder()
        CbOps.installInto(d)
        for (slot in 0x30..0x37) {
            assertThat(d.cb[slot]).`as`("SLL slot 0x%02X must be null", slot).isNull()
        }
    }

    @Test
    fun `installInto registers exactly 56 documented rotate-shift opcodes in CB 0x00 to 0x3F`() {
        val d = Decoder()
        CbOps.installInto(d)
        val count = (0x00..0x3F).count { d.cb[it] != null }
        assertThat(count).isEqualTo(56)
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
}
