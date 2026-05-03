package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class LdIxFromAddrTest {
    @Test
    fun `LD IX (nn) reads 16-bit value from memory, 20 T-states, pc by 4, r by 2`() {
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
                write(0x102, 0x00)
                write(0x103, 0x40) // addr = 0x4000
                write(0x4000, 0x34)
                write(0x4001, 0x12) // value = 0x1234
            }
        LdIxFromAddr(idx = IndexReg.IX).execute(cpu, mem)
        assertThat(cpu.ix).isEqualTo(0x1234)
        assertThat(cpu.pc).isEqualTo(0x104)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(20L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `LD IY (nn) writes IY`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0
            }
        val mem =
            Memory().apply {
                write(0x102, 0x00)
                write(0x103, 0x40)
                write(0x4000, 0xCD)
                write(0x4001, 0xAB)
            }
        LdIxFromAddr(idx = IndexReg.IY).execute(cpu, mem)
        assertThat(cpu.iy).isEqualTo(0xABCD)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdIxFromAddr(idx = IndexReg.IX).mnemonic { 0 }).isEqualTo("LD IX, (nn)")
        assertThat(LdIxFromAddr(idx = IndexReg.IY).mnemonic { 0 }).isEqualTo("LD IY, (nn)")
    }

    @Test
    fun `operandLength=2, baseCycles=20`() {
        val op = LdIxFromAddr(idx = IndexReg.IX)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(20)
    }
}
