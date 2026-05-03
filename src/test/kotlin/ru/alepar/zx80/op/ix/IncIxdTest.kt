package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Memory

class IncIxdTest {
    @Test
    fun `INC (IX+5) increments byte at IX+5, 23 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                ix = 0x4000
            }
        val mem =
            Memory().apply {
                write(0x102, 0x05)
                write(0x4005, 0x05)
            }
        IncIxd(idx = IndexReg.IX).execute(cpu, mem)
        assertThat(mem.read(0x4005)).isEqualTo(0x06)
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(23L)
    }

    @Test
    fun `INC (IX+d) preserves C flag`() {
        val cpu =
            Cpu().apply {
                ix = 0x4000
                f = Flags.C
            }
        val mem =
            Memory().apply {
                write(0x102, 0)
                write(0x4000, 0x05)
            }
        IncIxd(idx = IndexReg.IX).execute(cpu, mem)
        assertThat(cpu.f and Flags.C).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(IncIxd(idx = IndexReg.IX).mnemonic { 0 }).isEqualTo("INC (IX+d)")
        assertThat(IncIxd(idx = IndexReg.IY).mnemonic { 0 }).isEqualTo("INC (IY+d)")
    }

    @Test
    fun `operandLength=1, baseCycles=23`() {
        val op = IncIxd(idx = IndexReg.IX)
        assertThat(op.operandLength).isEqualTo(1)
        assertThat(op.baseCycles).isEqualTo(23)
    }
}
