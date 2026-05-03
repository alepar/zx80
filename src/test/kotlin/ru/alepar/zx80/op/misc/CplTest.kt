package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class CplTest {
    @Test
    fun `CPL xors A with 0xFF, sets H and N, preserves S Z PV C`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x12
                f = Flags.S or Flags.Z or Flags.PV or Flags.C
            }
        Cpl.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0xED)
        assertThat(cpu.f and Flags.H).isNotZero
        assertThat(cpu.f and Flags.N).isNotZero
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Cpl.mnemonic { 0 }).isEqualTo("CPL")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(Cpl.operandLength).isZero
        assertThat(Cpl.baseCycles).isEqualTo(4)
    }
}
