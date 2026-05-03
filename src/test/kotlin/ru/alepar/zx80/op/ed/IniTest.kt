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
        // byte 0x42 has bit 7 = 0, so N is clear (Sean Young rule).
        assertThat(cpu.f and Flags.N).isZero
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

    @Test
    fun `INI X and Y come from bits 5 and 3 of B after decrement (Sean Young TUZD)`() {
        // B = 0x29 (0010 1001) before decrement. After: B = 0x28 (0010 1000): bit 5 = 1 -> X;
        // bit 3 = 1 -> Y.
        val cpu =
            Cpu().apply {
                hl = 0x4000
                bc = 0x297F
            }
        Ini.execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x28)
        assertThat(cpu.f and Flags.X).isNotZero
        assertThat(cpu.f and Flags.Y).isNotZero
    }

    @Test
    fun `INI N comes from bit 7 of port byte`() {
        // default IoBus returns 0xFF; bit 7 = 1 -> N set.
        val cpu = Cpu().apply { bc = 0x0200 }
        Ini.execute(cpu, Memory())
        assertThat(cpu.f and Flags.N).isNotZero
    }

    @Test
    fun `INI carry and half-carry from temp = byte + (C+1) overflow`() {
        // default IoBus returns 0xFF; C = 0x80 -> temp = 0xFF + 0x81 = 0x180 > 0xFF -> C=H=1.
        val cpu = Cpu().apply { bc = 0x0280 }
        Ini.execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.f and Flags.H).isNotZero
    }

    @Test
    fun `INI clears carry when temp does not overflow`() {
        val zeroIo =
            object : IoBus {
                override fun read(port: Int) = 0x00

                override fun write(port: Int, value: Int) {}
            }
        val cpu =
            Cpu().apply {
                bc = 0x0200
                io = zeroIo
            }
        Ini.execute(cpu, Memory())
        assertThat(cpu.f and Flags.C).isZero
        assertThat(cpu.f and Flags.H).isZero
    }
}
