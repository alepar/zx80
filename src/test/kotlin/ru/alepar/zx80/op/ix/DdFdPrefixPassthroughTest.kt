package ru.alepar.zx80.op.ix

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.misc.Nop

class DdFdPrefixPassthroughTest {
    @Test
    fun `wrapping NOP, PC advances by 2, R bumped twice, T-states += 8`() {
        val passthrough = DdFdPrefixPassthrough(Nop)
        val cpu = Cpu().apply { pc = 0x100; r = 0; tStates = 0L }
        passthrough.execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `operandLength is 1 plus wrapped`() {
        // Nop's operandLength is 0; passthrough should be 1.
        val passthrough = DdFdPrefixPassthrough(Nop)
        assertThat(passthrough.operandLength).isEqualTo(1)
    }

    @Test
    fun `baseCycles is 4 plus wrapped`() {
        // Nop's baseCycles is 4; passthrough should be 8.
        val passthrough = DdFdPrefixPassthrough(Nop)
        assertThat(passthrough.baseCycles).isEqualTo(8)
    }

    @Test
    fun `mnemonic includes DD slash FD prefix`() {
        val passthrough = DdFdPrefixPassthrough(Nop)
        assertThat(passthrough.mnemonic { 0 }).startsWith("DD/FD ")
    }
}
