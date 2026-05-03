package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class DdFdNopPrefixTest {
    @Test
    fun `advances pc by 1, bumps r by 1, adds 4 T-states, leaves all else untouched`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x42
                f = 0x55
            }
        DdFdNopPrefix.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f).isEqualTo(0x55)
    }

    @Test
    fun `mnemonic and metadata`() {
        assertThat(DdFdNopPrefix.mnemonic { 0 }).isEqualTo("NOP* (DD/FD prefix)")
        assertThat(DdFdNopPrefix.operandLength).isZero
        assertThat(DdFdNopPrefix.baseCycles).isEqualTo(4)
    }
}
