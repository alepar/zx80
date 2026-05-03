package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdRegFromIxdTest {
    @Test
    fun `LD B, (IX+5) reads byte at IX+5 into B, advances pc by 3, r by 2, 19 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                b = 0
                f = 0xFF
            }
        val mem =
            Memory().apply {
                write(0x100, 0xDD)
                write(0x101, 0x46)
                write(0x102, 0x05)
                write(0x4005, 0x42)
            }
        LdRegFromIxd(idx = IndexReg.IX, dst = Reg.B).execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(19L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `LD A, (IY-2) handles negative displacement`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0x4000
                a = 0
            }
        val mem =
            Memory().apply {
                write(0x102, 0xFE)
                write(0x3FFE, 0x99)
            }
        LdRegFromIxd(idx = IndexReg.IY, dst = Reg.A).execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x99)
    }

    @Test
    fun `LD H, (IX+1) wraps when IX near end of memory`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                ix = 0xFFFF
            }
        val mem =
            Memory().apply {
                write(0x102, 0x01)
                write(0x0000, 0x42)
            }
        LdRegFromIxd(idx = IndexReg.IX, dst = Reg.H).execute(cpu, mem)
        assertThat(cpu.h).isEqualTo(0x42)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdRegFromIxd(idx = IndexReg.IX, dst = Reg.B).mnemonic { 0 })
            .isEqualTo("LD B, (IX+d)")
        assertThat(LdRegFromIxd(idx = IndexReg.IY, dst = Reg.A).mnemonic { 0 })
            .isEqualTo("LD A, (IY+d)")
    }

    @Test
    fun `operandLength=1, baseCycles=19`() {
        val op = LdRegFromIxd(idx = IndexReg.IX, dst = Reg.B)
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(19)
    }
}
