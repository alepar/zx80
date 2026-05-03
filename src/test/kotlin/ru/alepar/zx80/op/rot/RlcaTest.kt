package ru.alepar.zx80.op.rot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.Memory

class RlcaTest {
    @Test
    fun `RLCA rotates A left, bit 7 to C and to bit 0`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0x85
            }
        Rlca.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x0B)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(4L)
    }

    @Test
    fun `RLCA with bit 7 clear leaves C clear`() {
        val cpu = Cpu().apply { a = 0x42 }
        Rlca.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x84)
        assertThat(cpu.f and Flags.C).isZero
    }

    @Test
    fun `RLCA preserves S Z PV from oldF`() {
        val cpu =
            Cpu().apply {
                a = 0x42
                f = Flags.S or Flags.Z or Flags.PV
            }
        Rlca.execute(cpu, Memory())
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.N).isZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Rlca.mnemonic { 0 }).isEqualTo("RLCA")
    }

    @Test
    fun `operandLength=0, baseCycles=4`() {
        assertThat(Rlca.operandLength).isZero
        assertThat(Rlca.baseCycles).isEqualTo(4)
    }
}
