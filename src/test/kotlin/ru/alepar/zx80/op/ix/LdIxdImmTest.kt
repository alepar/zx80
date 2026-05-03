package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class LdIxdImmTest {
    @Test
    fun `LD (IX+5) imm writes 0x42 to memory at IX+5, advances pc by 4, r by 2, 19 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x100, 0xDD)
                write(0x101, 0x36)
                write(0x102, 0x05)
                write(0x103, 0x42)
            }
        LdIxdImm(idx = IndexReg.IX).execute(cpu, mem)
        assertThat(mem.read(0x4005)).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(19L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `LD (IY-2) imm handles negative displacement`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0x4000
            }
        val mem =
            Memory().apply {
                write(0x102, 0xFE)
                write(0x103, 0x99)
            }
        LdIxdImm(idx = IndexReg.IY).execute(cpu, mem)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x99)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdIxdImm(idx = IndexReg.IX).mnemonic { 0 }).isEqualTo("LD (IX+d), n")
        assertThat(LdIxdImm(idx = IndexReg.IY).mnemonic { 0 }).isEqualTo("LD (IY+d), n")
    }

    @Test
    fun `operandLength=2, baseCycles=19`() {
        val op = LdIxdImm(idx = IndexReg.IX)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(19)
    }
}
