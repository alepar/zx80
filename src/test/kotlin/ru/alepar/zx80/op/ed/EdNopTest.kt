package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class EdNopTest {
    @Test
    fun `EdNop advances pc by 2, bumps r by 2, adds 8 T-states, leaves all else untouched`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x42
                f = 0x55
            }
        EdNop.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(cpu.f).isEqualTo(0x55)
    }

    @Test
    fun `EdNop mnemonic and metadata`() {
        assertThat(EdNop.mnemonic { 0 }).isEqualTo("NOP*")
        assertThat(EdNop.operandLength).isZero
        assertThat(EdNop.baseCycles).isEqualTo(8)
    }
}
