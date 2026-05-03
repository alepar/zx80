package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.Memory

class LdIxHalfImmTest {
    @Test
    fun `LD IXH, n loads byte at pc+2 into IXH`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0xAACC
            }
        // immediate at pc+2 = 0x102 (DD 26 nn)
        val mem = Memory().apply { write(0x102, 0x42) }
        LdIxHalfImm(IndexHalfReg.IXH).execute(cpu, mem)
        assertThat(cpu.ix).isEqualTo(0x42CC)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `LD IYL, n loads byte at pc+2 into IYL`() {
        val cpu =
            Cpu().apply {
                pc = 0x200
                iy = 0xAA00
            }
        val mem = Memory().apply { write(0x202, 0x99) }
        LdIxHalfImm(IndexHalfReg.IYL).execute(cpu, mem)
        assertThat(cpu.iy).isEqualTo(0xAA99)
    }

    @Test
    fun `flags untouched`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                ix = 0
                f = 0xAA
            }
        val mem = Memory().apply { write(0x102, 0x33) }
        LdIxHalfImm(IndexHalfReg.IXH).execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `mnemonic and metadata`() {
        val op = LdIxHalfImm(IndexHalfReg.IXH)
        assertThat(op.mnemonic { 0 }).isEqualTo("LD IXH, n")
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(11)
    }
}
