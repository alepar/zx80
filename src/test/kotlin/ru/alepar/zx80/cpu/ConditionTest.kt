package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ConditionTest {

    @Test
    fun `NZ is true when Z flag is clear, false when set`() {
        val cpu = Cpu()
        cpu.f = 0
        assertThat(Condition.NZ.test(cpu)).isTrue
        cpu.f = Flags.Z
        assertThat(Condition.NZ.test(cpu)).isFalse
    }

    @Test
    fun `Z is true when Z flag is set, false when clear`() {
        val cpu = Cpu()
        cpu.f = Flags.Z
        assertThat(Condition.Z.test(cpu)).isTrue
        cpu.f = 0
        assertThat(Condition.Z.test(cpu)).isFalse
    }

    @Test
    fun `NC is true when C flag is clear`() {
        val cpu = Cpu()
        cpu.f = 0
        assertThat(Condition.NC.test(cpu)).isTrue
        cpu.f = Flags.C
        assertThat(Condition.NC.test(cpu)).isFalse
    }

    @Test
    fun `C is true when C flag is set`() {
        val cpu = Cpu()
        cpu.f = Flags.C
        assertThat(Condition.C.test(cpu)).isTrue
    }

    @Test
    fun `PO is true when PV flag is clear (parity odd)`() {
        val cpu = Cpu()
        cpu.f = 0
        assertThat(Condition.PO.test(cpu)).isTrue
        cpu.f = Flags.PV
        assertThat(Condition.PO.test(cpu)).isFalse
    }

    @Test
    fun `PE is true when PV flag is set`() {
        val cpu = Cpu().apply { f = Flags.PV }
        assertThat(Condition.PE.test(cpu)).isTrue
    }

    @Test
    fun `P is true when S flag is clear (positive)`() {
        val cpu = Cpu()
        cpu.f = 0
        assertThat(Condition.P.test(cpu)).isTrue
        cpu.f = Flags.S
        assertThat(Condition.P.test(cpu)).isFalse
    }

    @Test
    fun `M is true when S flag is set (minus)`() {
        val cpu = Cpu().apply { f = Flags.S }
        assertThat(Condition.M.test(cpu)).isTrue
    }

    @Test
    fun `mnemonic matches enum name`() {
        assertThat(Condition.NZ.mnemonic).isEqualTo("NZ")
        assertThat(Condition.PO.mnemonic).isEqualTo("PO")
        assertThat(Condition.M.mnemonic).isEqualTo("M")
    }

    @Test
    fun `fromBits maps Z80 ccc encoding`() {
        assertThat(Condition.fromBits(0)).isEqualTo(Condition.NZ)
        assertThat(Condition.fromBits(1)).isEqualTo(Condition.Z)
        assertThat(Condition.fromBits(2)).isEqualTo(Condition.NC)
        assertThat(Condition.fromBits(3)).isEqualTo(Condition.C)
        assertThat(Condition.fromBits(4)).isEqualTo(Condition.PO)
        assertThat(Condition.fromBits(5)).isEqualTo(Condition.PE)
        assertThat(Condition.fromBits(6)).isEqualTo(Condition.P)
        assertThat(Condition.fromBits(7)).isEqualTo(Condition.M)
    }

    @Test
    fun `fromBits rejects out-of-range`() {
        assertThatThrownBy { Condition.fromBits(8) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Condition.fromBits(-1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
