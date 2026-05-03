package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.RegPair

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
}
