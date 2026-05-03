package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class LdOpsTest {
    @Test
    fun `installInto registers all 49 LD r,r prime opcodes in the 0x40-0x7F block`() {
        val d = Decoder()
        LdOps.installInto(d)
        assertThat((d.main[0x40] as LdRegReg).mnemonic { 0 }).isEqualTo("LD B, B")
        assertThat((d.main[0x41] as LdRegReg).mnemonic { 0 }).isEqualTo("LD B, C")
        assertThat((d.main[0x47] as LdRegReg).mnemonic { 0 }).isEqualTo("LD B, A")
        assertThat((d.main[0x78] as LdRegReg).mnemonic { 0 }).isEqualTo("LD A, B")
        assertThat((d.main[0x7F] as LdRegReg).mnemonic { 0 }).isEqualTo("LD A, A")
    }

    @Test
    fun `installInto registers LD r,(HL) opcodes`() {
        val d = Decoder()
        LdOps.installInto(d)
        assertThat((d.main[0x46] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD B, (HL)")
        assertThat((d.main[0x4E] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD C, (HL)")
        assertThat((d.main[0x56] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD D, (HL)")
        assertThat((d.main[0x5E] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD E, (HL)")
        assertThat((d.main[0x66] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD H, (HL)")
        assertThat((d.main[0x6E] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD L, (HL)")
        assertThat((d.main[0x7E] as LdRegFromHl).mnemonic { 0 }).isEqualTo("LD A, (HL)")
    }

    @Test
    fun `installInto registers LD (HL),r opcodes`() {
        val d = Decoder()
        LdOps.installInto(d)
        assertThat((d.main[0x70] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), B")
        assertThat((d.main[0x71] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), C")
        assertThat((d.main[0x72] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), D")
        assertThat((d.main[0x73] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), E")
        assertThat((d.main[0x74] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), H")
        assertThat((d.main[0x75] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), L")
        assertThat((d.main[0x77] as LdHlFromReg).mnemonic { 0 }).isEqualTo("LD (HL), A")
    }

    @Test
    fun `installInto leaves the HALT slot at 0x76 untouched`() {
        val d = Decoder()
        LdOps.installInto(d)
        assertThat(d.main[0x76]).isNull()
    }

    @Test
    fun `installInto registers exactly 63 opcodes in the 0x40-0x7F block`() {
        val d = Decoder()
        LdOps.installInto(d)
        val count = (0x40..0x7F).count { d.main[it] != null }
        assertThat(count).isEqualTo(63)
    }
}
