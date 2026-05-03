package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class IxOpsTest {
    @Test
    fun `installInto registers LD SP, HL straggler at main 0xF9`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat(d.main[0xF9]).isSameAs(LdSpHl)
    }

    @Test
    fun `installInto registers LD IX nn at DD 0x21 and LD IY nn at FD 0x21`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0x21] as LdIxImm).mnemonic { 0 }).isEqualTo("LD IX, nn")
        assertThat((d.fd[0x21] as LdIxImm).mnemonic { 0 }).isEqualTo("LD IY, nn")
    }

    @Test
    fun `installInto registers INC IX at DD 0x23 and INC IY at FD 0x23`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0x23] as IncIx).mnemonic { 0 }).isEqualTo("INC IX")
        assertThat((d.fd[0x23] as IncIx).mnemonic { 0 }).isEqualTo("INC IY")
    }

    @Test
    fun `installInto registers ADD IX IX at DD 0x29 (Self uses idx not HL)`() {
        val d = Decoder()
        IxOps.installInto(d)
        val op = d.dd[0x29] as AddIxPair
        assertThat(op.mnemonic { 0 }).isEqualTo("ADD IX, IX")
    }

    @Test
    fun `installInto registers PUSH IX at DD 0xE5 and POP IY at FD 0xE1`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0xE5] as PushIx).mnemonic { 0 }).isEqualTo("PUSH IX")
        assertThat((d.fd[0xE1] as PopIx).mnemonic { 0 }).isEqualTo("POP IY")
    }

    @Test
    fun `installInto fills 13 pair-touching opcodes per index in dd and fd tables`() {
        val d = Decoder()
        IxOps.installInto(d)
        val ddCount = d.dd.count { it != null }
        val fdCount = d.fd.count { it != null }
        assertThat(ddCount).isEqualTo(13)
        assertThat(fdCount).isEqualTo(13)
    }
}
