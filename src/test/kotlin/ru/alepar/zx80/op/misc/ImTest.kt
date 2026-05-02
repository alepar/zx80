package ru.alepar.zx80.op.misc

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

class ImTest {
    @Test
    fun `Im(0) sets im=0, advances pc by 2, r by 2, adds 8 T-states`() {
        val cpu =
            Cpu().apply {
                pc = 0x100
                r = 0x10
                tStates = 0L
                im = 1
            }
        Im(0).execute(cpu, Memory())
        assertThat(cpu.im).isZero
        assertThat(cpu.pc).isEqualTo(0x102)
        assertThat(cpu.r).isEqualTo(0x12)
        assertThat(cpu.tStates).isEqualTo(8L)
    }

    @Test
    fun `Im(1) sets im=1`() {
        val cpu = Cpu().apply { im = 0 }
        Im(1).execute(cpu, Memory())
        assertThat(cpu.im).isEqualTo(1)
    }

    @Test
    fun `Im(2) sets im=2`() {
        val cpu = Cpu().apply { im = 0 }
        Im(2).execute(cpu, Memory())
        assertThat(cpu.im).isEqualTo(2)
    }

    @Test
    fun `Im rejects mode outside 0 to 2`() {
        assertThatThrownBy { Im(3) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Im(-1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `mnemonic includes mode`() {
        assertThat(Im(0).mnemonic { 0 }).isEqualTo("IM 0")
        assertThat(Im(1).mnemonic { 0 }).isEqualTo("IM 1")
        assertThat(Im(2).mnemonic { 0 }).isEqualTo("IM 2")
    }
}
