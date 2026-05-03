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
    fun `installInto registers LD r,(IX+d) at DD 0x46, 0x4E, 0x7E`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0x46] as LdRegFromIxd).mnemonic { 0 }).isEqualTo("LD B, (IX+d)")
        assertThat((d.dd[0x4E] as LdRegFromIxd).mnemonic { 0 }).isEqualTo("LD C, (IX+d)")
        assertThat((d.dd[0x7E] as LdRegFromIxd).mnemonic { 0 }).isEqualTo("LD A, (IX+d)")
    }

    @Test
    fun `installInto registers LD (IY+d) r at FD 0x70 to 0x77 (skipping rrr=110)`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.fd[0x70] as LdIxdFromReg).mnemonic { 0 }).isEqualTo("LD (IY+d), B")
        assertThat((d.fd[0x77] as LdIxdFromReg).mnemonic { 0 }).isEqualTo("LD (IY+d), A")
        assertThat(d.fd[0x76]).isNull()
    }

    @Test
    fun `installInto registers LD (IX+d) n at DD 0x36`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0x36] as LdIxdImm).mnemonic { 0 }).isEqualTo("LD (IX+d), n")
    }

    @Test
    fun `installInto registers ALU A,(IX+d) at DD 0x86 and 0xBE`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0x86] as AluAFromIxd).mnemonic { 0 }).isEqualTo("ADD A, (IX+d)")
        assertThat((d.dd[0xBE] as AluAFromIxd).mnemonic { 0 }).isEqualTo("CP A, (IX+d)")
    }

    @Test
    fun `installInto registers INC (IX+d) at DD 0x34 and DEC (IY+d) at FD 0x35`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0x34] as IncIxd).mnemonic { 0 }).isEqualTo("INC (IX+d)")
        assertThat((d.fd[0x35] as DecIxd).mnemonic { 0 }).isEqualTo("DEC (IY+d)")
    }
}
