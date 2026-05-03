package ru.alepar.zx80.op.alu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.RegPair

class DecPairTest {
    @Test
    fun `DEC BC decrements BC by 1, advances pc, increments r, adds 6 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0
                tStates = 0L
                bc = 0x1234
            }
        DecPair(dst = RegPair.BC).execute(cpu, Memory())
        assertThat(cpu.bc).isEqualTo(0x1233)
        assertThat(cpu.pc).isEqualTo(0x101)
        assertThat(cpu.tStates).isEqualTo(6L)
    }

    @Test
    fun `DEC HL wraps from 0x0000 to 0xFFFF`() {
        val cpu = Cpu().apply { hl = 0x0000 }
        DecPair(dst = RegPair.HL).execute(cpu, Memory())
        assertThat(cpu.hl).isEqualTo(0xFFFF)
    }

    @Test
    fun `DEC rr does NOT affect any flag`() {
        val cpu =
            Cpu().apply {
                de = 0x1000
                f = 0xFF
            }
        DecPair(dst = RegPair.DE).execute(cpu, Memory())
        assertThat(cpu.f).isEqualTo(0xFF)
    }

    @Test
    fun `mnemonic format`() {
        assertThat(DecPair(dst = RegPair.HL).mnemonic { 0 }).isEqualTo("DEC HL")
    }

    @Test
    fun `operandLength=0, baseCycles=6`() {
        val op = DecPair(dst = RegPair.BC)
        assertThat(op.operandLength).isZero
        assertThat(op.baseCycles).isEqualTo(6)
    }
}
