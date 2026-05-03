package ru.alepar.zx80.op.branch

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class RstTest {
    @Test
    fun `RST 18H pushes pc+1 and jumps to 0x18, 11 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                sp = 0x4000
            }
        val mem = Memory()
        Rst(target = 0x18).execute(cpu, mem)
        assertThat(cpu.pc).isEqualTo(0x18)
        assertThat(cpu.sp).isEqualTo(0x3FFE)
        assertThat(mem.read(0x3FFE)).isEqualTo(0x01)
        assertThat(mem.read(0x3FFF)).isEqualTo(0x01)
        assertThat(cpu.tStates).isEqualTo(11L)
    }

    @Test
    fun `RST 00H jumps to 0x0000`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                sp = 0x4000
            }
        Rst(target = 0x00).execute(cpu, Memory())
        assertThat(cpu.pc).isZero
    }

    @Test
    fun `RST 38H jumps to 0x0038`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                sp = 0x4000
            }
        Rst(target = 0x38).execute(cpu, Memory())
        assertThat(cpu.pc).isEqualTo(0x38)
    }

    @Test
    fun `RST does NOT touch flags`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                sp = 0x4000
                f = 0xFF
            }
        Rst(target = 0x10).execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `Rst rejects target outside the 8 valid values`() {
        assertThatThrownBy { Rst(target = 0x05) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Rst(target = 0x40) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic`() {
        assertThat(Rst(target = 0x00).mnemonic { 0 }).isEqualTo("RST 00H")
        assertThat(Rst(target = 0x18).mnemonic { 0 }).isEqualTo("RST 18H")
        assertThat(Rst(target = 0x38).mnemonic { 0 }).isEqualTo("RST 38H")
    }

    @Test
    fun `operandLength=0, baseCycles=11`() {
        val op = Rst(target = 0x00)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(11)
    }
}
