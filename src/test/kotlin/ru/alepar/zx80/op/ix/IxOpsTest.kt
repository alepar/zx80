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

    @Test
    fun `installInto registers JP (IX) at DD 0xE9 and JP (IY) at FD 0xE9`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0xE9] as JpIx).mnemonic { 0 }).isEqualTo("JP (IX)")
        assertThat((d.fd[0xE9] as JpIx).mnemonic { 0 }).isEqualTo("JP (IY)")
    }

    @Test
    fun `installInto registers ALU on IXH at DD 84-BC and ALU on IXL at DD 85-BD`() {
        val d = Decoder()
        IxOps.installInto(d)
        // Spot-check ADD A, IXH at DD 84
        assertThat((d.dd[0x84] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("ADD A, IXH")
        // ADC A, IXH at DD 8C
        assertThat((d.dd[0x8C] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("ADC A, IXH")
        // SUB IXL at DD 95
        assertThat((d.dd[0x95] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("SUB A, IXL")
        // CP IXL at DD BD
        assertThat((d.dd[0xBD] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("CP A, IXL")
    }

    @Test
    fun `installInto registers ALU on IYH at FD 84-BC and IYL at FD 85-BD`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.fd[0x84] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("ADD A, IYH")
        assertThat((d.fd[0xAD] as AluAFromIxHalf).mnemonic { 0 }).isEqualTo("XOR A, IYL")
    }

    @Test
    fun `installInto registers INC IXH at DD 24 and DEC IXH at DD 25`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0x24] as IncIxHalf).mnemonic { 0 }).isEqualTo("INC IXH")
        assertThat((d.dd[0x25] as DecIxHalf).mnemonic { 0 }).isEqualTo("DEC IXH")
    }

    @Test
    fun `installInto registers INC IXL at DD 2C and DEC IXL at DD 2D`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0x2C] as IncIxHalf).mnemonic { 0 }).isEqualTo("INC IXL")
        assertThat((d.dd[0x2D] as DecIxHalf).mnemonic { 0 }).isEqualTo("DEC IXL")
    }

    @Test
    fun `installInto registers INC IYH at FD 24, DEC IYH at FD 25, INC IYL at FD 2C, DEC IYL at FD 2D`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.fd[0x24] as IncIxHalf).mnemonic { 0 }).isEqualTo("INC IYH")
        assertThat((d.fd[0x25] as DecIxHalf).mnemonic { 0 }).isEqualTo("DEC IYH")
        assertThat((d.fd[0x2C] as IncIxHalf).mnemonic { 0 }).isEqualTo("INC IYL")
        assertThat((d.fd[0x2D] as DecIxHalf).mnemonic { 0 }).isEqualTo("DEC IYL")
    }

    @Test
    fun `installInto registers LD half,n at DD 26, DD 2E, FD 26, FD 2E`() {
        val d = Decoder()
        IxOps.installInto(d)
        assertThat((d.dd[0x26] as LdIxHalfImm).mnemonic { 0 }).isEqualTo("LD IXH, n")
        assertThat((d.dd[0x2E] as LdIxHalfImm).mnemonic { 0 }).isEqualTo("LD IXL, n")
        assertThat((d.fd[0x26] as LdIxHalfImm).mnemonic { 0 }).isEqualTo("LD IYH, n")
        assertThat((d.fd[0x2E] as LdIxHalfImm).mnemonic { 0 }).isEqualTo("LD IYL, n")
    }
}
