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
