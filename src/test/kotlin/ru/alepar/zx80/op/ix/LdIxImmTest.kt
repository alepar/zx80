package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class LdIxImmTest {
    @Test
    fun `LD IX nn reads little-endian immediate, advances pc by 4, r by 2, 14 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x100, 0xDD)
                write(0x101, 0x21)
                write(0x102, 0xCD)
                write(0x103, 0xAB)
            }
        LdIxImm(idx = IndexReg.IX).execute(cpu, mem)
        assertThat(cpu.ix).isEqualTo(0xABCD)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(14L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `LD IY nn writes IY`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0
            }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x103, 0x80)
            }
        LdIxImm(idx = IndexReg.IY).execute(cpu, mem)
        assertThat(cpu.iy).isEqualTo(0x8000)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdIxImm(idx = IndexReg.IX).mnemonic { 0 }).isEqualTo("LD IX, nn")
        assertThat(LdIxImm(idx = IndexReg.IY).mnemonic { 0 }).isEqualTo("LD IY, nn")
    }

    @Test
    fun `operandLength=2, baseCycles=14`() {
        val op = LdIxImm(idx = IndexReg.IX)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(14)
    }
}
