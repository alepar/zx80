package ru.alepar.zx80.op.ixcb

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class IxCbOpsTest {

    @Test
    fun `installInto registers RLC RRC RL RR SLA SRA SRL (IX+d) at DDCB rrr=110 slots`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat((d.ddcb[0x06] as RotShiftIxd).mnemonic { 0 }).isEqualTo("RLC (IX+d)")
        assertThat((d.ddcb[0x0E] as RotShiftIxd).mnemonic { 0 }).isEqualTo("RRC (IX+d)")
        assertThat((d.ddcb[0x16] as RotShiftIxd).mnemonic { 0 }).isEqualTo("RL (IX+d)")
        assertThat((d.ddcb[0x1E] as RotShiftIxd).mnemonic { 0 }).isEqualTo("RR (IX+d)")
        assertThat((d.ddcb[0x26] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SLA (IX+d)")
        assertThat((d.ddcb[0x2E] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SRA (IX+d)")
        assertThat((d.ddcb[0x3E] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SRL (IX+d)")
    }

    @Test
    fun `installInto registers same shape for FDCB (IY+d)`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat((d.fdcb[0x06] as RotShiftIxd).mnemonic { 0 }).isEqualTo("RLC (IY+d)")
        assertThat((d.fdcb[0x3E] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SRL (IY+d)")
    }

    @Test
    fun `installInto leaves SLL slots (DDCB 0x36, FDCB 0x36) null`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat(d.ddcb[0x36]).isNull()
        assertThat(d.fdcb[0x36]).isNull()
    }

    @Test
    fun `installInto leaves undocumented copy-to-r rotate slots null (rrr is not 110)`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat(d.ddcb[0x00]).isNull()
        assertThat(d.ddcb[0x07]).isNull()
        assertThat(d.ddcb[0x37]).isNull()
    }

    @Test
    fun `installInto registers exactly 7 documented rotate-shift opcodes per index`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        val ddcbRotShiftCount = (0x00..0x3F).count { d.ddcb[it] != null }
        val fdcbRotShiftCount = (0x00..0x3F).count { d.fdcb[it] != null }
        assertThat(ddcbRotShiftCount).isEqualTo(7)
        assertThat(fdcbRotShiftCount).isEqualTo(7)
    }

    @Test
    fun `installInto registers BIT n (IX+d) at DDCB 0x46 0x4E 0x56 to 0x7E`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat((d.ddcb[0x46] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 0, (IX+d)")
        assertThat((d.ddcb[0x4E] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 1, (IX+d)")
        assertThat((d.ddcb[0x76] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 6, (IX+d)")
        assertThat((d.ddcb[0x7E] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 7, (IX+d)")
    }

    @Test
    fun `installInto registers BIT n (IY+d) at FDCB 0x46 to 0x7E`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat((d.fdcb[0x46] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 0, (IY+d)")
        assertThat((d.fdcb[0x7E] as BitIxd).mnemonic { 0 }).isEqualTo("BIT 7, (IY+d)")
    }

    @Test
    fun `installInto registers exactly 8 BIT opcodes per index in DDCB 0x40 to 0x7F`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        val ddcbBitCount = (0x40..0x7F).count { d.ddcb[it] != null }
        assertThat(ddcbBitCount).isEqualTo(8)
    }
}
