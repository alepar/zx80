package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegRegPrefixedTest {
    @Test
    fun `LD B, C with DD-style prefix copies C to B and advances pc by 2`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                b = 0x11
                c = 0x22
                f = 0xAA
            }
        LdRegRegPrefixed(src = Reg.C, dst = Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x22)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic does not include prefix - same as unprefixed LD r,r prime`() {
        assertThat(LdRegRegPrefixed(src = Reg.C, dst = Reg.B).mnemonic { 0 }).isEqualTo("LD B, C")
        assertThat(LdRegRegPrefixed(src = Reg.A, dst = Reg.E).mnemonic { 0 }).isEqualTo("LD E, A")
    }

    @Test
    fun `operandLength=0, baseCycles=8`() {
        val op = LdRegRegPrefixed(src = Reg.B, dst = Reg.C)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(8)
    }
}
