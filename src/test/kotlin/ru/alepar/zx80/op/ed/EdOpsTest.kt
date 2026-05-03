package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

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
}
