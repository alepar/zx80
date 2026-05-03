package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.misc.Im

class EdOpsTest {
    @Test
    fun `installInto registers register transfers at ED 47, 4F, 57, 5F`() {
        val d = Decoder()
        EdOps.installInto(d)
        assertThat(d.ed[0x47]).isSameAs(LdIA)
        assertThat(d.ed[0x4F]).isSameAs(LdRA)
        assertThat(d.ed[0x57]).isSameAs(LdAI)
        assertThat(d.ed[0x5F]).isSameAs(LdAR)
    }

    @Test
    fun `installInto registers NEG at ED 44, RETN at 45, RETI at 4D`() {
        val d = Decoder()
        EdOps.installInto(d)
        assertThat(d.ed[0x44]).isSameAs(Neg)
        assertThat(d.ed[0x45]).isSameAs(Retn)
        assertThat(d.ed[0x4D]).isSameAs(Reti)
    }

    @Test
    fun `installInto registers RRD at ED 67, RLD at 6F`() {
        val d = Decoder()
        EdOps.installInto(d)
        assertThat(d.ed[0x67]).isSameAs(Rrd)
        assertThat(d.ed[0x6F]).isSameAs(Rld)
    }

    @Test
    fun `installInto registers LD (nn), rr at ED 43, 53, 63, 73`() {
        val d = Decoder()
        EdOps.installInto(d)
        for (ppBits in 0..3) {
            val pair = RegPair.fromBits(ppBits)
            val op = d.ed[0x43 or (ppBits shl 4)]
            assertThat(op).isInstanceOf(LdAddrFromPair::class.java)
            assertThat(op!!.mnemonic { 0 }).isEqualTo("LD (nn), ${pair.mnemonic}")
        }
    }

    @Test
    fun `installInto registers IN A,(n) at 0xDB and OUT (n),A at 0xD3`() {
        val d = Decoder()
        EdOps.installInto(d)
        assertThat(d.main[0xDB]).isSameAs(InAImm)
        assertThat(d.main[0xD3]).isSameAs(OutImmA)
    }

    @Test
    fun `installInto registers single I_O ED 40 50 58 60 68 78 (IN r, (C))`() {
        val d = Decoder()
        EdOps.installInto(d)
        // ED 40, 48, 50, 58, 60, 68, 78 — non-(HL) targets.
        for (rrrBits in listOf(0, 1, 2, 3, 4, 5, 7)) {
            val op = d.ed[0x40 or (rrrBits shl 3)]
            assertThat(op).isInstanceOf(InRC::class.java)
        }
        assertThat(d.ed[0x70]).isSameAs(InCFlags)
    }

    @Test
    fun `installInto registers OUT (C) variants`() {
        val d = Decoder()
        EdOps.installInto(d)
        for (rrrBits in listOf(0, 1, 2, 3, 4, 5, 7)) {
            val op = d.ed[0x41 or (rrrBits shl 3)]
            assertThat(op).isInstanceOf(OutCR::class.java)
        }
        assertThat(d.ed[0x71]).isSameAs(OutCZero)
    }

    @Test
    fun `installInto registers block I_O at A2, AA, B2, BA, A3, AB, B3, BB`() {
        val d = Decoder()
        EdOps.installInto(d)
        assertThat(d.ed[0xA2]).isSameAs(Ini)
        assertThat(d.ed[0xAA]).isSameAs(Ind)
        assertThat(d.ed[0xB2]).isSameAs(Inir)
        assertThat(d.ed[0xBA]).isSameAs(Indr)
        assertThat(d.ed[0xA3]).isSameAs(Outi)
        assertThat(d.ed[0xAB]).isSameAs(Outd)
        assertThat(d.ed[0xB3]).isSameAs(Otir)
        assertThat(d.ed[0xBB]).isSameAs(Otdr)
    }

    @Test
    fun `installInto registers block compare at ED A1, A9, B1, B9`() {
        val d = Decoder()
        EdOps.installInto(d)
        assertThat(d.ed[0xA1]).isSameAs(Cpi)
        assertThat(d.ed[0xA9]).isSameAs(Cpd)
        assertThat(d.ed[0xB1]).isSameAs(Cpir)
        assertThat(d.ed[0xB9]).isSameAs(Cpdr)
    }

    @Test
    fun `installInto registers block move at ED A0, A8, B0, B8`() {
        val d = Decoder()
        EdOps.installInto(d)
        assertThat(d.ed[0xA0]).isSameAs(Ldi)
        assertThat(d.ed[0xA8]).isSameAs(Ldd)
        assertThat(d.ed[0xB0]).isSameAs(Ldir)
        assertThat(d.ed[0xB8]).isSameAs(Lddr)
    }

    @Test
    fun `installInto registers LD rr, (nn) at ED 4B, 5B, 6B, 7B`() {
        val d = Decoder()
        EdOps.installInto(d)
        for (ppBits in 0..3) {
            val pair = RegPair.fromBits(ppBits)
            val op = d.ed[0x4B or (ppBits shl 4)]
            assertThat(op).isInstanceOf(LdPairFromAddr::class.java)
            assertThat(op!!.mnemonic { 0 }).isEqualTo("LD ${pair.mnemonic}, (nn)")
        }
    }

    @Test
    fun `installInto registers NEG at 7 alternate slots besides ED 0x44`() {
        val d = Decoder()
        EdOps.installInto(d)
        val canonical = d.ed[0x44]
        assertThat(canonical).isSameAs(Neg)
        for (alt in listOf(0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C)) {
            assertThat(d.ed[alt]).`as`("ED 0x%02X NEG alias", alt).isSameAs(Neg)
        }
    }

    @Test
    fun `installInto registers RETN at 3 alternate slots besides ED 0x45`() {
        val d = Decoder()
        EdOps.installInto(d)
        assertThat(d.ed[0x45]).isSameAs(Retn)
        for (alt in listOf(0x55, 0x65, 0x75)) {
            assertThat(d.ed[alt]).`as`("ED 0x%02X RETN alias", alt).isSameAs(Retn)
        }
    }

    @Test
    fun `installInto registers RETI at 3 alternate slots besides ED 0x4D`() {
        val d = Decoder()
        EdOps.installInto(d)
        assertThat(d.ed[0x4D]).isSameAs(Reti)
        for (alt in listOf(0x5D, 0x6D, 0x7D)) {
            assertThat(d.ed[alt]).`as`("ED 0x%02X RETI alias", alt).isSameAs(Reti)
        }
    }

    @Test
    fun `installInto registers IM 0 alias at ED 0x4E, 0x66, 0x6E`() {
        val d = Decoder()
        EdOps.installInto(d)
        for (alt in listOf(0x4E, 0x66, 0x6E)) {
            val op = d.ed[alt]
            assertThat(op).`as`("ED 0x%02X is IM 0", alt).isInstanceOf(Im::class.java)
            assertThat((op as Im).mode).`as`("ED 0x%02X mode", alt).isEqualTo(0)
        }
    }

    @Test
    fun `installInto registers IM 1 alias at ED 0x76`() {
        val d = Decoder()
        EdOps.installInto(d)
        val op = d.ed[0x76]
        assertThat(op).isInstanceOf(Im::class.java)
        assertThat((op as Im).mode).isEqualTo(1)
    }

    @Test
    fun `installInto registers IM 2 alias at ED 0x7E`() {
        val d = Decoder()
        EdOps.installInto(d)
        val op = d.ed[0x7E]
        assertThat(op).isInstanceOf(Im::class.java)
        assertThat((op as Im).mode).isEqualTo(2)
    }

    @Test
    fun `installInto leaves alternate slots NOT as EdNop`() {
        val d = Decoder()
        EdOps.installInto(d)
        val aliasSlots =
            listOf(
                0x4C,
                0x54,
                0x5C,
                0x64,
                0x6C,
                0x74,
                0x7C, // NEG
                0x55,
                0x65,
                0x75, // RETN
                0x5D,
                0x6D,
                0x7D, // RETI
                0x4E,
                0x66,
                0x6E,
                0x76,
                0x7E, // IM
            )
        for (slot in aliasSlots) {
            assertThat(d.ed[slot]).`as`("ED 0x%02X must not be EdNop", slot).isNotSameAs(EdNop)
        }
    }

    @Test
    fun `installInto fills all null ED slots with EdNop`() {
        val d = Decoder()
        EdOps.installInto(d)
        // Spot-check: ED 0x00 (no documented op there) is now EdNop
        assertThat(d.ed[0x00]).isSameAs(EdNop)
        // ED 0xFF (no documented op) is also EdNop
        assertThat(d.ed[0xFF]).isSameAs(EdNop)
        // Documented ED slots are NOT replaced — ED 0x44 = NEG should remain
        assertThat(d.ed[0x44]).isNotSameAs(EdNop)
        // Total ED slots are now 256 of 256 non-null
        val nonNull = (0..255).count { d.ed[it] != null }
        assertThat(nonNull).isEqualTo(256)
    }
}
