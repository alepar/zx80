package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.cpu.Memory

class OutCZeroTest {
    @Test
    fun `OUT (C), 0 writes 0 to port BC`() {
        val writes = mutableListOf<Pair<Int, Int>>()
        val cpu =
            Cpu().apply {
                bc = 0x1234
                io =
                    object : IoBus {
                        override fun read(port: Int) = 0xFF

                        override fun write(port: Int, value: Int) {
                            writes.add(port to value)
                        }
                    }
            }
        OutCZero.execute(cpu, Memory())
        assertThat(writes).containsExactly(0x1234 to 0)
        assertThat(cpu.tStates).isEqualTo(12L)
    }

    @Test
    fun `mnemonic`() {
        assertThat(OutCZero.mnemonic { 0 }).isEqualTo("OUT (C), 0")
    }
}
