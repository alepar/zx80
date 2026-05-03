package ru.alepar.zx80.op.ld

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class LdPairImmTest {
    @Test
    fun `LD BC, nn reads little-endian 16-bit immediate into BC`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                f = 0xAA
            }
        val mem =
            Memory().apply {
                write(0x100, 0x01) // LD BC, nn opcode
                write(0x101, 0xCD) // low byte of nn
                write(0x102, 0xAB) // high byte of nn
            }
        LdPairImm(pair = RegPair.BC).execute(cpu, mem)
        assertThat(cpu.bc).isEqualTo(0xABCD)
        assertThat(cpu.pc).isEqualTo(0x103)
        assertThat(cpu.r).isEqualTo(1)
        assertThat(cpu.tStates).isEqualTo(10L)
        assertThat(cpu.f).isEqualTo(0xAA)
    }

    @Test
    fun `LD SP, nn loads stack pointer`() {
        val cpu = Cpu().apply { pc = 0x200 }
        val mem =
            Memory().apply {
                write(0x200, 0x31)
                write(0x201, 0x00)
                write(0x202, 0x80)
            }
        LdPairImm(pair = RegPair.SP).execute(cpu, mem)
        assertThat(cpu.sp).isEqualTo(0x8000)
    }

    @Test
    fun `mnemonic`() {
        assertThat(LdPairImm(pair = RegPair.BC).mnemonic { 0 }).isEqualTo("LD BC, nn")
        assertThat(LdPairImm(pair = RegPair.HL).mnemonic { 0 }).isEqualTo("LD HL, nn")
        assertThat(LdPairImm(pair = RegPair.SP).mnemonic { 0 }).isEqualTo("LD SP, nn")
    }

    @Test
    fun `operandLength=2, baseCycles=10`() {
        val op = LdPairImm(pair = RegPair.BC)
        assertThat(op.operandLength).isEqualTo(2)
        assertThat(op.baseCycles).isEqualTo(10)
    }
}
