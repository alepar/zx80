package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class MiscOpsTest {
    @Test
    fun `installInto registers Nop, Halt, Di, Ei in main table`() {
        val d = Decoder()
        MiscOps.installInto(d)
        assertThat(d.main[0x00]).isSameAs(Nop)
        assertThat(d.main[0x76]).isSameAs(Halt)
        assertThat(d.main[0xF3]).isSameAs(Di)
        assertThat(d.main[0xFB]).isSameAs(Ei)
    }

    @Test
    fun `installInto registers DAA at 0x27, CPL at 0x2F, SCF at 0x37, CCF at 0x3F`() {
        val d = Decoder()
        MiscOps.installInto(d)
        assertThat(d.main[0x27]).isSameAs(Daa)
        assertThat(d.main[0x2F]).isSameAs(Cpl)
        assertThat(d.main[0x37]).isSameAs(Scf)
        assertThat(d.main[0x3F]).isSameAs(Ccf)
    }

    @Test
    fun `installInto registers IM 0, IM 1, IM 2 in ed table`() {
        val d = Decoder()
        MiscOps.installInto(d)
        assertThat(d.ed[0x46]).isInstanceOf(Im::class.java)
        assertThat(d.ed[0x56]).isInstanceOf(Im::class.java)
        assertThat(d.ed[0x5E]).isInstanceOf(Im::class.java)
        assertThat((d.ed[0x46] as Im).mnemonic { 0 }).isEqualTo("IM 0")
        assertThat((d.ed[0x56] as Im).mnemonic { 0 }).isEqualTo("IM 1")
        assertThat((d.ed[0x5E] as Im).mnemonic { 0 }).isEqualTo("IM 2")
    }
}
