package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Flags
import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.Reg

class InRCTest {
    @Test
    fun `IN B, (C) reads port BC into B and computes flags`() {
        val recordedPort = mutableListOf<Int>()
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                bc = 0xAB55
                f = Flags.C
                io =
                    object : IoBus {
                        override fun read(port: Int): Int {
                            recordedPort.add(port)
                            return 0x80
                        }

                        override fun write(port: Int, value: Int) {}
                    }
            }
        InRC(Reg.B).execute(cpu, Memory())
        assertThat(cpu.b).isEqualTo(0x80)
        // BC was 0xAB55; B was 0xAB; after writing 0x80, BC = 0x8055.
        assertThat(recordedPort).containsExactly(0xAB55)
        assertThat(cpu.f and Flags.S).isNotZero
        assertThat(cpu.f and Flags.Z).isZero
        assertThat(cpu.f and Flags.H).isZero
        assertThat(cpu.f and Flags.N).isZero
        assertThat(cpu.f and Flags.C).isNotZero
        assertThat(cpu.tStates).isEqualTo(12L)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(2)
    }

    @Test
    fun `IN H, (C) sets Z when value is 0 and PV when even parity`() {
        val cpu =
            Cpu().apply {
                bc = 0x0100
                io =
                    object : IoBus {
                        override fun read(port: Int) = 0

                        override fun write(port: Int, value: Int) {}
                    }
            }
        InRC(Reg.H).execute(cpu, Memory())
        assertThat(cpu.h).isZero
        assertThat(cpu.f and Flags.Z).isNotZero
        assertThat(cpu.f and Flags.PV).isNotZero
    }

    @Test
    fun `mnemonic format`() {
        assertThat(InRC(Reg.A).mnemonic { 0 }).isEqualTo("IN A, (C)")
        assertThat(InRC(Reg.E).mnemonic { 0 }).isEqualTo("IN E, (C)")
    }
}
