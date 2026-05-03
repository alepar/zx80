package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class AddIxPairTest {
    @Test
    fun `ADD IX, BC adds BC to IX, 15 T-states, pc by 2, r by 2`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x1000
                bc = 0x0234
                f = Flags.S or Flags.Z or Flags.PV
            }
        AddIxPair(idx = IndexReg.IX, srcBits = 0).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x1234)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(15L)
        // S, Z, PV preserved by Flags.afterAddWord
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `ADD IY, DE adds DE to IY`() {
        val cpu =
            Cpu().apply {
                iy = 0x1000
                de = 0x0234
            }
        AddIxPair(idx = IndexReg.IY, srcBits = 1).execute(cpu, Memory())
        assertThat(cpu.iy).isEqualTo(0x1234)
    }

    @Test
    fun `ADD IX, IX (srcBits=2 means Self) adds idx itself, NOT HL`() {
        val cpu =
            Cpu().apply {
                ix = 0x1234
                hl = 0x9999
            }
        AddIxPair(idx = IndexReg.IX, srcBits = 2).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x2468)
        assertThat(cpu.hl).isEqualTo(0x9999) // HL untouched
    }

    @Test
    fun `ADD IX, SP adds SP to IX`() {
        val cpu =
            Cpu().apply {
                ix = 0x1000
                sp = 0x0234
            }
        AddIxPair(idx = IndexReg.IX, srcBits = 3).execute(cpu, Memory())
        assertThat(cpu.ix).isEqualTo(0x1234)
    }

    @Test
    fun `carry from bit 15 sets C flag`() {
        val cpu =
            Cpu().apply {
                ix = 0xFFFF
                bc = 0x0001
            }
        AddIxPair(idx = IndexReg.IX, srcBits = 0).execute(cpu, Memory())
        assertThat(cpu.ix).isZero
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(AddIxPair(IndexReg.IX, 0).mnemonic { 0 }).isEqualTo("ADD IX, BC")
        assertThat(AddIxPair(IndexReg.IX, 1).mnemonic { 0 }).isEqualTo("ADD IX, DE")
        assertThat(AddIxPair(IndexReg.IX, 2).mnemonic { 0 }).isEqualTo("ADD IX, IX")
        assertThat(AddIxPair(IndexReg.IY, 2).mnemonic { 0 }).isEqualTo("ADD IY, IY")
        assertThat(AddIxPair(IndexReg.IX, 3).mnemonic { 0 }).isEqualTo("ADD IX, SP")
    }

    @Test
    fun `srcBits validation rejects out-of-range`() {
        assertThrows<IllegalArgumentException> { AddIxPair(IndexReg.IX, 4) }
        assertThrows<IllegalArgumentException> { AddIxPair(IndexReg.IX, -1) }
    }

    @Test
    fun `operandLength=0, baseCycles=15`() {
        val op = AddIxPair(IndexReg.IX, 0)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(15)
    }
}
