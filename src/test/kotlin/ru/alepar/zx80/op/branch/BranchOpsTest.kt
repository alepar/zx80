package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class BranchOpsTest {

    @Test
    fun `installInto registers JP nn at 0xC3`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat(d.main[0xC3]).isSameAs(JpAbs)
    }

    @Test
    fun `installInto registers JP (HL) at 0xE9`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat(d.main[0xE9]).isSameAs(JpHl)
    }

    @Test
    fun `installInto registers JP cc, nn at 0xC2 (NZ), 0xCA (Z), 0xD2 (NC), 0xDA (C), 0xE2 (PO), 0xEA (PE), 0xF2 (P), 0xFA (M)`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat((d.main[0xC2] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP NZ, nn")
        assertThat((d.main[0xCA] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP Z, nn")
        assertThat((d.main[0xD2] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP NC, nn")
        assertThat((d.main[0xDA] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP C, nn")
        assertThat((d.main[0xE2] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP PO, nn")
        assertThat((d.main[0xEA] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP PE, nn")
        assertThat((d.main[0xF2] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP P, nn")
        assertThat((d.main[0xFA] as JpAbsCc).mnemonic { 0 }).isEqualTo("JP M, nn")
    }

    @Test
    fun `installInto registers JR e at 0x18 and DJNZ e at 0x10`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat(d.main[0x18]).isSameAs(JrRel)
        assertThat(d.main[0x10]).isSameAs(Djnz)
    }

    @Test
    fun `installInto registers JR cc, e at 0x20 (NZ), 0x28 (Z), 0x30 (NC), 0x38 (C)`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat((d.main[0x20] as JrRelCc).mnemonic { 0 }).isEqualTo("JR NZ, e")
        assertThat((d.main[0x28] as JrRelCc).mnemonic { 0 }).isEqualTo("JR Z, e")
        assertThat((d.main[0x30] as JrRelCc).mnemonic { 0 }).isEqualTo("JR NC, e")
        assertThat((d.main[0x38] as JrRelCc).mnemonic { 0 }).isEqualTo("JR C, e")
    }
}
