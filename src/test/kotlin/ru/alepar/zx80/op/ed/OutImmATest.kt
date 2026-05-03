package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.cpu.Memory

class OutImmATest {
    @Test
    fun `OUT (n), A writes A to port (a shl 8) or n, 11T, no flags`() {
        val writes = mutableListOf<Pair<Int, Int>>()
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0xAB
                f = 0xFF
                io =
                    object : IoBus {
                        override fun read(port: Int) = 0xFF

                        override fun write(port: Int, value: Int) {
                            writes.add(port to value)
                        }
                    }
            }
        val mem =
            Memory().apply {
                write(0x100, 0xD3)
                write(0x101, 0x55)
            }
        OutImmA.execute(cpu, mem)
        assertThat(writes).containsExactly(0xAB55 to 0xAB)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(OutImmA.mnemonic { 0 }).isEqualTo("OUT (n), A")
    }
}
