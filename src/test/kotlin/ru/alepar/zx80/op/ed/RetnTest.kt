package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class RetnTest {
    @Test
    fun `RETN pops PC and copies IFF2 into IFF1`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                sp = 0x1000
                r = 0
                tStates = 0L
                iff1 = false
                iff2 = true
            }
        val mem =
            Memory().apply {
                write(0x1000, 0xAB)
                write(0x1001, 0xCD)
            }
        Retn.execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0xCDAB)
        assertThat(cpu.iff1).isTrue
        assertThat(cpu.tStates).isEqualTo(14L)
    }

    @Test
    fun `RETN with iff2 false clears iff1`() {
        val cpu =
            Cpu().apply {
                sp = 0x1000
                iff1 = true
                iff2 = false
            }
        Memory().apply {
            write(0x1000, 0)
            write(0x1001, 0)
        }
        Retn.execute(cpu, Memory())
        assertThat(cpu.iff1).isFalse
    }

    @Test
    fun `mnemonic`() {
        assertThat(Retn.mnemonic { 0 }).isEqualTo("RETN")
    }
}
