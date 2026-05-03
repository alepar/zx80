package ru.alepar.zx80.op.ed

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.cpu.Memory

class InAImmTest {
    @Test
    fun `IN A, (n) reads from port (a shl 8) or n into A, 11T, no flags`() {
        val recordedPort = mutableListOf<Int>()
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                a = 0xAB
                f = 0xFF
                io =
                    object : IoBus {
                        override fun read(port: Int): Int {
                            recordedPort.add(port)
                            return 0x42
                        }

                        override fun write(port: Int, value: Int) {}
                    }
            }
        val mem =
            Memory().apply {
                write(0x100, 0xDB)
                write(0x101, 0x55)
            }
        InAImm.execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0x42)
        assertThat(recordedPort).containsExactly(0xAB55)
        assertThat(cpu.f).isEqualTo(0xFF)
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `IN A, (n) with NoIoBus returns 0xFF`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                a = 0
            }
        val mem = Memory().apply { write(0x101, 0) }
        InAImm.execute(cpu, mem)
        assertThat(cpu.a).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic`() {
        assertThat(InAImm.mnemonic { 0 }).isEqualTo("IN A, (n)")
    }

    @Test
    fun `operandLength=1, baseCycles=11`() {
        assertThat(InAImm.operandLength).isEqualTo(1)
        assertThat(InAImm.baseCycles).isEqualTo(11)
    }

    @Test
    fun `IN A (n) sets cpu memptr to (A shl 8 or n) plus 1`() {
        val cpu =
            Cpu().apply {
                a = 0x27
                memptr = 0
            }
        val mem = Memory().apply { write(0x101, 0xFF) }
        cpu.pc = 0x100
        InAImm.execute(cpu, mem)
        // port = (0x27 shl 8) | 0xFF = 0x27FF; memptr = 0x27FF + 1 = 0x2800
        assertThat(cpu.memptr).isEqualTo(0x2800)
    }

    @Test
    fun `IN A (n) does not modify F`() {
        val cpu =
            Cpu().apply {
                a = 0x10
                f = 0x55
                memptr = 0
            }
        val mem = Memory().apply { write(0x101, 0x00) }
        cpu.pc = 0x100
        InAImm.execute(cpu, mem)
        assertThat(cpu.f).isEqualTo(0x55)
    }
}
