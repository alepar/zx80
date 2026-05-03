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
        assertThat(cpu.f and Flags.N).isNotZero
        assertThat(cpu.tStates).isEqualTo(16L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Outi.mnemonic { 0 }).isEqualTo("OUTI")
    }
}
