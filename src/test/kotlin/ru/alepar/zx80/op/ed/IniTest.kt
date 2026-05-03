package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.cpu.Memory

class IniTest {
    @Test
    fun `INI reads port BC into mem(HL), HL++, B--, 16T`() {
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x0355
                pc = 0x100
                tStates = 0L
                io =
                    object : IoBus {
                        override fun read(port: Int) = 0x42

                        override fun write(port: Int, value: Int) {}
                    }
            }
        val mem = Memory()
        Ini.execute(cpu, mem)
        assertThat(mem.read(0x4000)).isEqualTo(0x42)
        assertThat(cpu.hl).isEqualTo(0x4001)
        assertThat(cpu.b).isEqualTo(0x02)
        assertThat(cpu.f and Flags.N).isNotZero
        assertThat(cpu.f and Flags.Z).isZero
        assertThat(cpu.tStates).isEqualTo(16L)
        assertThat(cpu.pc).isEqualTo(0x102)
    }

    @Test
    fun `INI sets Z when B reaches 0`() {
        val cpu = Cpu().apply { bc = 0x0100 }
        Ini.execute(cpu, Memory())
        assertThat(cpu.b).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
    }

    @Test
    fun `mnemonic`() {
        assertThat(Ini.mnemonic { 0 }).isEqualTo("INI")
    }
}
