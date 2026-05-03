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

    @Test
    fun `installInto registers CALL nn at 0xCD`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat(d.main[0xCD]).isSameAs(CallAbs)
    }

    @Test
    fun `installInto registers CALL cc, nn at 0xC4 (NZ) through 0xFC (M)`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat((d.main[0xC4] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL NZ, nn")
        assertThat((d.main[0xCC] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL Z, nn")
        assertThat((d.main[0xD4] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL NC, nn")
        assertThat((d.main[0xDC] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL C, nn")
        assertThat((d.main[0xE4] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL PO, nn")
        assertThat((d.main[0xEC] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL PE, nn")
        assertThat((d.main[0xF4] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL P, nn")
        assertThat((d.main[0xFC] as CallAbsCc).mnemonic { 0 }).isEqualTo("CALL M, nn")
    }

    @Test
    fun `installInto registers RET at 0xC9`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat(d.main[0xC9]).isSameAs(Ret)
    }

    @Test
    fun `installInto registers RET cc at 0xC0 (NZ) through 0xF8 (M)`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat((d.main[0xC0] as RetCc).mnemonic { 0 }).isEqualTo("RET NZ")
        assertThat((d.main[0xC8] as RetCc).mnemonic { 0 }).isEqualTo("RET Z")
        assertThat((d.main[0xD0] as RetCc).mnemonic { 0 }).isEqualTo("RET NC")
        assertThat((d.main[0xD8] as RetCc).mnemonic { 0 }).isEqualTo("RET C")
        assertThat((d.main[0xE0] as RetCc).mnemonic { 0 }).isEqualTo("RET PO")
        assertThat((d.main[0xE8] as RetCc).mnemonic { 0 }).isEqualTo("RET PE")
        assertThat((d.main[0xF0] as RetCc).mnemonic { 0 }).isEqualTo("RET P")
        assertThat((d.main[0xF8] as RetCc).mnemonic { 0 }).isEqualTo("RET M")
    }

    @Test
    fun `installInto registers RST p at 0xC7 (00H) through 0xFF (38H)`() {
        val d = Decoder()
        BranchOps.installInto(d)
        assertThat((d.main[0xC7] as Rst).mnemonic { 0 }).isEqualTo("RST 00H")
        assertThat((d.main[0xCF] as Rst).mnemonic { 0 }).isEqualTo("RST 08H")
        assertThat((d.main[0xD7] as Rst).mnemonic { 0 }).isEqualTo("RST 10H")
        assertThat((d.main[0xDF] as Rst).mnemonic { 0 }).isEqualTo("RST 18H")
        assertThat((d.main[0xE7] as Rst).mnemonic { 0 }).isEqualTo("RST 20H")
        assertThat((d.main[0xEF] as Rst).mnemonic { 0 }).isEqualTo("RST 28H")
        assertThat((d.main[0xF7] as Rst).mnemonic { 0 }).isEqualTo("RST 30H")
        assertThat((d.main[0xFF] as Rst).mnemonic { 0 }).isEqualTo("RST 38H")
    }
}
