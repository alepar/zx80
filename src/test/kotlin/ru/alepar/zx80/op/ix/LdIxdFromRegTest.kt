package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class LdIxdFromRegTest {
    @Test
    fun `LD (IX+5), B writes B to memory at IX+5, 19 T-states, pc by 3, r by 2`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
                b = 0x42
                f = 0xFF
            }
        val mem = Memory().apply { write(0x102, 0x05) }
        LdIxdFromReg(idx = IndexReg.IX, src = Reg.B).execute(cpu, mem)
        assertThat(mem.read(0x4005)).isEqualTo(0x42)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(19L)
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `LD (IY-2), A handles negative displacement`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                iy = 0x4000
                a = 0x99
            }
        val mem = Memory().apply { write(0x102, 0xFE) }
        LdIxdFromReg(idx = IndexReg.IY, src = Reg.A).execute(cpu, mem)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x99)
    }

    @Test
    fun `LD (IX+1), L wraps when IX near end of memory`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                ix = 0xFFFF
                l = 0x42
            }
        val mem = Memory().apply { write(0x102, 0x01) }
        LdIxdFromReg(idx = IndexReg.IX, src = Reg.L).execute(cpu, mem)
        assertThat(mem.read(0x0000)).isEqualTo(0x42)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdIxdFromReg(idx = IndexReg.IX, src = Reg.B).mnemonic { 0 })
            .isEqualTo("LD (IX+d), B")
        assertThat(LdIxdFromReg(idx = IndexReg.IY, src = Reg.A).mnemonic { 0 })
            .isEqualTo("LD (IY+d), A")
    }

    @Test
    fun `operandLength=1, baseCycles=19`() {
        val op = LdIxdFromReg(idx = IndexReg.IX, src = Reg.B)
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(19)
    }
}
