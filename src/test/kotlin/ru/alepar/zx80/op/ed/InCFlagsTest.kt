package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.cpu.Memory

class InCFlagsTest {
    @Test
    fun `IN (C) reads port BC, sets flags only, no register write`() {
        val cpu =
            Cpu().apply {
                bc = 0x0100
                a = 0x55
                b = 0x01
                f = Flags.C
                io =
                    object : IoBus {
                        override fun read(port: Int) = 0x80

                        override fun write(port: Int, value: Int) {}
                    }
            }
        InCFlags.execute(cpu, Memory())
        assertThat(cpu.a).isEqualTo(0x55)
        assertThat(cpu.b).isEqualTo(0x01)
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.tStates).isEqualTo(12L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(InCFlags.mnemonic { 0 }).isEqualTo("IN (C)")
    }

    @Test
    fun `IN (C) sets cpu memptr to BC plus 1`() {
        val cpu =
            Cpu().apply {
                bc = 0x27FF
                memptr = 0
            }
        InCFlags.execute(cpu, Memory())
        assertThat(cpu.memptr).isEqualTo(0x2800)
    }

    @Test
    fun `IN (C) sets X and Y from byte read`() {
        val cpu =
            Cpu().apply {
                bc = 0x27FF
                memptr = 0
            }
        InCFlags.execute(cpu, Memory())
        // default IoBus returns 0xFF -> X+Y
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isNotZero
    }
}
