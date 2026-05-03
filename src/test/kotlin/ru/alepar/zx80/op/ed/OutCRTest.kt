package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class OutCRTest {
    @Test
    fun `OUT (C), B writes B to port BC, no flag changes, 12T`() {
        val writes = mutableListOf<Pair<Int, Int>>()
        val cpu =
            Cpu().apply {
                bc = 0xAB55
                f = 0xFF
                io =
                    object : IoBus {
                        override fun read(port: Int) = 0xFF

                        override fun write(port: Int, value: Int) {
                            writes.add(port to value)
                        }
                    }
            }
        OutCR(Reg.B).execute(cpu, Memory())
        assertThat(writes).containsExactly(0xAB55 to 0xAB)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.tStates).isEqualTo(12L)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(OutCR(Reg.A).mnemonic { 0 }).isEqualTo("OUT (C), A")
    }
}
