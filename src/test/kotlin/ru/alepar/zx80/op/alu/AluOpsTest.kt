package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Decoder

class AluOpsTest {
    @Test
    fun `installInto registers ADD A,r at 0x80 through 0x87 minus 0x86`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0x80] as AluAReg).mnemonic { 0 }).isEqualTo("ADD A, B")
        assertThat((d.main[0x81] as AluAReg).mnemonic { 0 }).isEqualTo("ADD A, C")
        assertThat((d.main[0x87] as AluAReg).mnemonic { 0 }).isEqualTo("ADD A, A")
    }

    @Test
    fun `installInto registers SUB A,r at 0x90 onwards`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0x90] as AluAReg).mnemonic { 0 }).isEqualTo("SUB A, B")
        assertThat((d.main[0x97] as AluAReg).mnemonic { 0 }).isEqualTo("SUB A, A")
    }

    @Test
    fun `installInto registers XOR A,r at 0xA8 onwards (note XOR at ooo=5)`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0xA8] as AluAReg).mnemonic { 0 }).isEqualTo("XOR A, B")
        assertThat((d.main[0xAF] as AluAReg).mnemonic { 0 }).isEqualTo("XOR A, A")
    }

    @Test
    fun `installInto registers OR A,r at 0xB0 onwards (note OR at ooo=6)`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0xB0] as AluAReg).mnemonic { 0 }).isEqualTo("OR A, B")
        assertThat((d.main[0xB7] as AluAReg).mnemonic { 0 }).isEqualTo("OR A, A")
    }

    @Test
    fun `installInto registers CP A,r at 0xB8 onwards`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0xB8] as AluAReg).mnemonic { 0 }).isEqualTo("CP A, B")
        assertThat((d.main[0xBF] as AluAReg).mnemonic { 0 }).isEqualTo("CP A, A")
    }

    @Test
    fun `installInto registers exactly 56 reg-source ALU opcodes in 0x80-0xBF`() {
        val d = Decoder()
        AluOps.installInto(d)
        val count = (0x80..0xBF).count { d.main[it] is AluAReg }
        assertThat(count).isEqualTo(56)
    }

    @Test
    fun `installInto registers ALU A,(HL) at 0x86, 0x8E, 0x96, 0x9E, 0xA6, 0xAE, 0xB6, 0xBE`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0x86] as AluAFromHl).mnemonic { 0 }).isEqualTo("ADD A, (HL)")
        assertThat((d.main[0x8E] as AluAFromHl).mnemonic { 0 }).isEqualTo("ADC A, (HL)")
        assertThat((d.main[0x96] as AluAFromHl).mnemonic { 0 }).isEqualTo("SUB A, (HL)")
        assertThat((d.main[0x9E] as AluAFromHl).mnemonic { 0 }).isEqualTo("SBC A, (HL)")
        assertThat((d.main[0xA6] as AluAFromHl).mnemonic { 0 }).isEqualTo("AND A, (HL)")
        assertThat((d.main[0xAE] as AluAFromHl).mnemonic { 0 }).isEqualTo("XOR A, (HL)")
        assertThat((d.main[0xB6] as AluAFromHl).mnemonic { 0 }).isEqualTo("OR A, (HL)")
        assertThat((d.main[0xBE] as AluAFromHl).mnemonic { 0 }).isEqualTo("CP A, (HL)")
    }

    @Test
    fun `installInto registers ALU A,n at 0xC6, 0xCE, 0xD6, 0xDE, 0xE6, 0xEE, 0xF6, 0xFE`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0xC6] as AluAImm).mnemonic { 0 }).isEqualTo("ADD A, n")
        assertThat((d.main[0xCE] as AluAImm).mnemonic { 0 }).isEqualTo("ADC A, n")
        assertThat((d.main[0xD6] as AluAImm).mnemonic { 0 }).isEqualTo("SUB A, n")
        assertThat((d.main[0xDE] as AluAImm).mnemonic { 0 }).isEqualTo("SBC A, n")
        assertThat((d.main[0xE6] as AluAImm).mnemonic { 0 }).isEqualTo("AND A, n")
        assertThat((d.main[0xEE] as AluAImm).mnemonic { 0 }).isEqualTo("XOR A, n")
        assertThat((d.main[0xF6] as AluAImm).mnemonic { 0 }).isEqualTo("OR A, n")
        assertThat((d.main[0xFE] as AluAImm).mnemonic { 0 }).isEqualTo("CP A, n")
    }

    @Test
    fun `installInto registers INC r at 0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x3C`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0x04] as IncReg).mnemonic { 0 }).isEqualTo("INC B")
        assertThat((d.main[0x0C] as IncReg).mnemonic { 0 }).isEqualTo("INC C")
        assertThat((d.main[0x14] as IncReg).mnemonic { 0 }).isEqualTo("INC D")
        assertThat((d.main[0x1C] as IncReg).mnemonic { 0 }).isEqualTo("INC E")
        assertThat((d.main[0x24] as IncReg).mnemonic { 0 }).isEqualTo("INC H")
        assertThat((d.main[0x2C] as IncReg).mnemonic { 0 }).isEqualTo("INC L")
        assertThat((d.main[0x3C] as IncReg).mnemonic { 0 }).isEqualTo("INC A")
    }

    @Test
    fun `installInto registers INC (HL) at 0x34`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat(d.main[0x34]).isSameAs(IncHlMem)
    }

    @Test
    fun `installInto registers DEC r at 0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x3D`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0x05] as DecReg).mnemonic { 0 }).isEqualTo("DEC B")
        assertThat((d.main[0x0D] as DecReg).mnemonic { 0 }).isEqualTo("DEC C")
        assertThat((d.main[0x15] as DecReg).mnemonic { 0 }).isEqualTo("DEC D")
        assertThat((d.main[0x1D] as DecReg).mnemonic { 0 }).isEqualTo("DEC E")
        assertThat((d.main[0x25] as DecReg).mnemonic { 0 }).isEqualTo("DEC H")
        assertThat((d.main[0x2D] as DecReg).mnemonic { 0 }).isEqualTo("DEC L")
        assertThat((d.main[0x3D] as DecReg).mnemonic { 0 }).isEqualTo("DEC A")
    }

    @Test
    fun `installInto registers DEC (HL) at 0x35`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat(d.main[0x35]).isSameAs(DecHlMem)
    }

    @Test
    fun `installInto registers ADD HL,rr at 0x09, 0x19, 0x29, 0x39`() {
        val d = Decoder()
        AluOps.installInto(d)
        assertThat((d.main[0x09] as AddHlPair).mnemonic { 0 }).isEqualTo("ADD HL, BC")
        assertThat((d.main[0x19] as AddHlPair).mnemonic { 0 }).isEqualTo("ADD HL, DE")
        assertThat((d.main[0x29] as AddHlPair).mnemonic { 0 }).isEqualTo("ADD HL, HL")
        assertThat((d.main[0x39] as AddHlPair).mnemonic { 0 }).isEqualTo("ADD HL, SP")
    }
}
