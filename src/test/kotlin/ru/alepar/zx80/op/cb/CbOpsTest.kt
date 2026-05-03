package ru.alepar.zx80.op.cb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder
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
}
