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

    @Test
    fun `installInto registers RES n (IX+d) at DDCB 0x86 to 0xBE`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat((d.ddcb[0x86] as ResIxd).mnemonic { 0 }).isEqualTo("RES 0, (IX+d)")
        assertThat((d.ddcb[0x8E] as ResIxd).mnemonic { 0 }).isEqualTo("RES 1, (IX+d)")
        assertThat((d.ddcb[0xBE] as ResIxd).mnemonic { 0 }).isEqualTo("RES 7, (IX+d)")
    }

    @Test
    fun `installInto registers SET n (IY+d) at FDCB 0xC6 to 0xFE`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat((d.fdcb[0xC6] as SetIxd).mnemonic { 0 }).isEqualTo("SET 0, (IY+d)")
        assertThat((d.fdcb[0xFE] as SetIxd).mnemonic { 0 }).isEqualTo("SET 7, (IY+d)")
    }

    @Test
    fun `installInto registers SLL (IX+d) at DDCB 0x36`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        assertThat((d.ddcb[0x36] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SLL (IX+d)")
        assertThat((d.fdcb[0x36] as RotShiftIxd).mnemonic { 0 }).isEqualTo("SLL (IY+d)")
    }

    @Test
    fun `installInto registers RotShiftIxdCopyback at non-rrr=6 slots`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        // DDCB 0x00 = RLC (IX+d), B
        assertThat((d.ddcb[0x00] as RotShiftIxdCopyback).mnemonic { 0 }).isEqualTo("RLC (IX+d), B")
        // DDCB 0x07 = RLC (IX+d), A
        assertThat((d.ddcb[0x07] as RotShiftIxdCopyback).mnemonic { 0 }).isEqualTo("RLC (IX+d), A")
        // DDCB 0x30 = SLL (IX+d), B (oooBits=6, rrr=0)
        assertThat((d.ddcb[0x30] as RotShiftIxdCopyback).mnemonic { 0 }).isEqualTo("SLL (IX+d), B")
        // FDCB 0x3F = SRL (IY+d), A (oooBits=7, rrr=7)
        assertThat((d.fdcb[0x3F] as RotShiftIxdCopyback).mnemonic { 0 }).isEqualTo("SRL (IY+d), A")
    }

    @Test
    fun `installInto fills the entire rotate-shift block 0x00-0x3F under DDCB and FDCB`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        val ddCount = (0x00..0x3F).count { d.ddcb[it] != null }
        val fdCount = (0x00..0x3F).count { d.fdcb[it] != null }
        assertThat(ddCount).isEqualTo(64)
        assertThat(fdCount).isEqualTo(64)
    }

    @Test
    fun `installInto registers ResIxdCopyback at non-rrr=6 slots`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        // DDCB 0x80 = RES 0, (IX+d), B
        assertThat((d.ddcb[0x80] as ResIxdCopyback).mnemonic { 0 }).isEqualTo("RES 0, (IX+d), B")
        // DDCB 0x87 = RES 0, (IX+d), A
        assertThat((d.ddcb[0x87] as ResIxdCopyback).mnemonic { 0 }).isEqualTo("RES 0, (IX+d), A")
        // DDCB 0xBF = RES 7, (IX+d), A
        assertThat((d.ddcb[0xBF] as ResIxdCopyback).mnemonic { 0 }).isEqualTo("RES 7, (IX+d), A")
        // FDCB 0xB0 = RES 6, (IY+d), B
        assertThat((d.fdcb[0xB0] as ResIxdCopyback).mnemonic { 0 }).isEqualTo("RES 6, (IY+d), B")
    }

    @Test
    fun `installInto fills the entire RES block 0x80-0xBF under DDCB and FDCB`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        val ddCount = (0x80..0xBF).count { d.ddcb[it] != null }
        val fdCount = (0x80..0xBF).count { d.fdcb[it] != null }
        assertThat(ddCount).isEqualTo(64)
        assertThat(fdCount).isEqualTo(64)
    }

    @Test
    fun `installInto registers SetIxdCopyback at non-rrr=6 slots`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        // DDCB 0xC0 = SET 0, (IX+d), B
        assertThat((d.ddcb[0xC0] as SetIxdCopyback).mnemonic { 0 }).isEqualTo("SET 0, (IX+d), B")
        // DDCB 0xC7 = SET 0, (IX+d), A
        assertThat((d.ddcb[0xC7] as SetIxdCopyback).mnemonic { 0 }).isEqualTo("SET 0, (IX+d), A")
        // DDCB 0xFF = SET 7, (IX+d), A
        assertThat((d.ddcb[0xFF] as SetIxdCopyback).mnemonic { 0 }).isEqualTo("SET 7, (IX+d), A")
        // FDCB 0xCF = SET 1, (IY+d), A
        assertThat((d.fdcb[0xCF] as SetIxdCopyback).mnemonic { 0 }).isEqualTo("SET 1, (IY+d), A")
    }

    @Test
    fun `installInto fills the entire SET block 0xC0-0xFF under DDCB and FDCB`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        val ddCount = (0xC0..0xFF).count { d.ddcb[it] != null }
        val fdCount = (0xC0..0xFF).count { d.fdcb[it] != null }
        assertThat(ddCount).isEqualTo(64)
        assertThat(fdCount).isEqualTo(64)
    }

    @Test
    fun `installInto fills 200 slots in DDCB and FDCB tables (BIT mirror intentionally out of scope)`() {
        val d = Decoder()
        IxCbOps.installInto(d)
        // 64 (rotshift block) + 8 (BIT block — documented rrr=6 only; BIT undocumented mirror is
        // out of scope per spec) + 64 (RES block) + 64 (SET block) = 200.
        val ddCount = (0..255).count { d.ddcb[it] != null }
        val fdCount = (0..255).count { d.fdcb[it] != null }
        assertThat(ddCount).isEqualTo(200)
        assertThat(fdCount).isEqualTo(200)
    }
}
