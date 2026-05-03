package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.cpu.Memory

class OutiTest {
    @Test
    fun `OUTI writes mem(HL) to port BC, decrements B (before write), increments HL, 16T`() {
        val writes = mutableListOf<Pair<Int, Int>>()
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0355
                io =
                    object : IoBus {
                        override fun read(port: Int) = 0xFF

                        override fun write(port: Int, value: Int) {
                            writes.add(port to value)
                        }
                    }
            }
        val mem = Memory().apply { write(0x4000, 0x42) }
        Outi.execute(cpu, mem)
        // B was 0x03; decremented to 0x02 BEFORE write, so port = 0x0255 (B=2, C=0x55).
        assertThat(writes).containsExactly(0x0255 to 0x42)
        assertThat(cpu.hl).isEqualTo(0x4001)
        assertThat(cpu.b).isEqualTo(0x02)
        // byte 0x42 has bit 7 = 0, so N is clear (Sean Young rule).
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Outi.mnemonic { 0 }).isEqualTo("OUTI")
    }

    @Test
    fun `OUTI X and Y come from bits 5 and 3 of B after decrement`() {
        // B = 0x29 -> after = 0x28: bit 5 = 1 -> X; bit 3 = 1 -> Y.
        val cpu =
            Cpu().apply {
                hl = 0x4018
                bc = 0x2907
            }
        val mem = Memory().apply { write(0x4018, 0x10) }
        Outi.execute(cpu, mem)
        assertThat(cpu.b).isEqualTo(0x28)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isNotZero
    }

    @Test
    fun `OUTI N comes from bit 7 of byte read from HL`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0200
            }
        val mem = Memory().apply { write(0x4000, 0x80) }
        Outi.execute(cpu, mem)
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `OUTI carry and half-carry from temp = byte + post-update L overflow`() {
        // HL = 0x40FF -> after HL++ = 0x4100 -> L_after = 0x00. byte = 0xFF. temp = 0xFF + 0x00 =
        // 0xFF -> not > 0xFF -> C=H=0.
        val cpu =
            Cpu().apply {
                hl = 0x40FF
                bc = 0x0200
            }
        val mem = Memory().apply { write(0x40FF, 0xFF) }
        Outi.execute(cpu, mem)
        assertThat(cpu.f and Flags.C).isZero
        assertThat(cpu.f and Flags.H).isZero
    }

    @Test
    fun `OUTI carry sets when temp overflows`() {
        // HL = 0x4080 -> after HL++ = 0x4081 -> L_after = 0x81. byte = 0x80. temp = 0x80 + 0x81 =
        // 0x101 > 0xFF -> C=H=1.
        val cpu =
            Cpu().apply {
                hl = 0x4080
                bc = 0x0200
            }
        val mem = Memory().apply { write(0x4080, 0x80) }
        Outi.execute(cpu, mem)
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.H).isNotZero
    }
}
