package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlagsTest {
    @Test
    fun `parity is true for even number of 1 bits`() {
        assertThat(Flags.parity(0x00)).isTrue
        assertThat(Flags.parity(0x03)).isTrue
        assertThat(Flags.parity(0xFF)).isTrue
        assertThat(Flags.parity(0x55)).isTrue
    }

    @Test
    fun `parity is false for odd number of 1 bits`() {
        assertThat(Flags.parity(0x01)).isFalse
        assertThat(Flags.parity(0x07)).isFalse
        assertThat(Flags.parity(0x80)).isFalse
        assertThat(Flags.parity(0x7F)).isFalse
    }

    @Test
    fun `parity ignores bits above bit 7`() {
        assertThat(Flags.parity(0xFF00)).isTrue
        assertThat(Flags.parity(0xFF01)).isFalse
    }

    @Test
    fun `afterAdd 0x01 + 0x02 with no carry`() {
        val r = Flags.afterAdd(0x01, 0x02, 0)
        assertThat(r.value).isEqualTo(0x03)
        assertThat(r.newF and Flags.S).isZero
        assertThat(r.newF and Flags.Z).isZero
        assertThat(r.newF and Flags.H).isZero
        assertThat(r.newF and Flags.PV).isZero
        assertThat(r.newF and Flags.N).isZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterAdd half-carry boundary 0x0F + 0x01 sets H`() {
        val r = Flags.afterAdd(0x0F, 0x01, 0)
        assertThat(r.value).isEqualTo(0x10)
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.Z).isZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterAdd carry-out 0xFF + 0x01 sets C and Z`() {
        val r = Flags.afterAdd(0xFF, 0x01, 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.H).isNotZero
    }

    @Test
    fun `afterAdd overflow 0x7F + 0x01 sets V and S`() {
        val r = Flags.afterAdd(0x7F, 0x01, 0)
        assertThat(r.value).isEqualTo(0x80)
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterAdd carry-in folds in 0x01 + 0x01 + 1 = 0x03`() {
        val r = Flags.afterAdd(0x01, 0x01, 1)
        assertThat(r.value).isEqualTo(0x03)
    }

    @Test
    fun `afterAdd zero result 0x00 + 0x00 sets Z`() {
        val r = Flags.afterAdd(0x00, 0x00, 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterSub 0x05 - 0x03 with no borrow`() {
        val r = Flags.afterSub(0x05, 0x03, 0)
        assertThat(r.value).isEqualTo(0x02)
        assertThat(r.newF and Flags.N).isNotZero
        assertThat(r.newF and Flags.Z).isZero
        assertThat(r.newF and Flags.C).isZero
        assertThat(r.newF and Flags.H).isZero
    }

    @Test
    fun `afterSub equal operands sets Z, no borrow`() {
        val r = Flags.afterSub(0x42, 0x42, 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.N).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterSub borrow 0x00 - 0x01 sets C and S, result 0xFF`() {
        val r = Flags.afterSub(0x00, 0x01, 0)
        assertThat(r.value).isEqualTo(0xFF)
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.N).isNotZero
    }

    @Test
    fun `afterSub half-borrow 0x10 - 0x01 sets H`() {
        val r = Flags.afterSub(0x10, 0x01, 0)
        assertThat(r.value).isEqualTo(0x0F)
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterSub overflow 0x80 - 0x01 sets V`() {
        val r = Flags.afterSub(0x80, 0x01, 0)
        assertThat(r.value).isEqualTo(0x7F)
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.S).isZero
    }

    @Test
    fun `afterSub borrow-in folds in 0x05 - 0x02 - 1 = 0x02`() {
        val r = Flags.afterSub(0x05, 0x02, 1)
        assertThat(r.value).isEqualTo(0x02)
    }

    @Test
    fun `afterAnd 0xFF AND 0x0F = 0x0F, H set, C cleared, P=parity`() {
        val r = Flags.afterAnd(0xFF, 0x0F)
        assertThat(r.value).isEqualTo(0x0F)
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.N).isZero
        assertThat(r.newF and Flags.C).isZero
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.Z).isZero
        assertThat(r.newF and Flags.S).isZero
    }

    @Test
    fun `afterAnd zero result sets Z`() {
        val r = Flags.afterAnd(0xF0, 0x0F)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.H).isNotZero
    }

    @Test
    fun `afterAnd negative result sets S`() {
        val r = Flags.afterAnd(0xFF, 0x80)
        assertThat(r.value).isEqualTo(0x80)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.PV).isZero
    }

    @Test
    fun `afterOr 0x0F OR 0xF0 = 0xFF, H cleared, C cleared`() {
        val r = Flags.afterOr(0x0F, 0xF0)
        assertThat(r.value).isEqualTo(0xFF)
        assertThat(r.newF and Flags.H).isZero
        assertThat(r.newF and Flags.N).isZero
        assertThat(r.newF and Flags.C).isZero
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.S).isNotZero
    }

    @Test
    fun `afterOr zero result`() {
        val r = Flags.afterOr(0x00, 0x00)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
    }

    @Test
    fun `afterXor 0xFF XOR 0x0F = 0xF0`() {
        val r = Flags.afterXor(0xFF, 0x0F)
        assertThat(r.value).isEqualTo(0xF0)
        assertThat(r.newF and Flags.H).isZero
        assertThat(r.newF and Flags.N).isZero
        assertThat(r.newF and Flags.C).isZero
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.S).isNotZero
    }

    @Test
    fun `afterXor self gives zero`() {
        val r = Flags.afterXor(0x42, 0x42)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.PV).isNotZero
    }

    @Test
    fun `afterInc 0x05 becomes 0x06, C preserved from oldF`() {
        val r = Flags.afterInc(0x05, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x06)
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.N).isZero
        assertThat(r.newF and Flags.Z).isZero
        assertThat(r.newF and Flags.S).isZero
        assertThat(r.newF and Flags.H).isZero
        assertThat(r.newF and Flags.PV).isZero
    }

    @Test
    fun `afterInc 0x0F to 0x10 sets H`() {
        val r = Flags.afterInc(0x0F, oldF = 0)
        assertThat(r.value).isEqualTo(0x10)
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterInc 0xFF to 0x00 sets Z and H, NOT C`() {
        val r = Flags.afterInc(0xFF, oldF = 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterInc 0x7F to 0x80 sets V and S`() {
        val r = Flags.afterInc(0x7F, oldF = 0)
        assertThat(r.value).isEqualTo(0x80)
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.H).isNotZero
    }

    @Test
    fun `afterDec 0x05 to 0x04, C preserved`() {
        val r = Flags.afterDec(0x05, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x04)
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.N).isNotZero
        assertThat(r.newF and Flags.H).isZero
    }

    @Test
    fun `afterDec 0x10 to 0x0F sets H (half-borrow)`() {
        val r = Flags.afterDec(0x10, oldF = 0)
        assertThat(r.value).isEqualTo(0x0F)
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.N).isNotZero
    }

    @Test
    fun `afterDec 0x01 to 0x00 sets Z`() {
        val r = Flags.afterDec(0x01, oldF = 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.H).isZero
    }

    @Test
    fun `afterDec 0x80 to 0x7F sets V`() {
        val r = Flags.afterDec(0x80, oldF = 0)
        assertThat(r.value).isEqualTo(0x7F)
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.S).isZero
    }

    @Test
    fun `afterDec 0x00 to 0xFF sets H and S, NOT C`() {
        val r = Flags.afterDec(0x00, oldF = 0)
        assertThat(r.value).isEqualTo(0xFF)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterAddWord 0x1234 + 0x5678 with no carry`() {
        val r = Flags.afterAddWord(0x1234, 0x5678, oldF = 0)
        assertThat(r.value).isEqualTo(0x68AC)
        assertThat(r.newF and Flags.N).isZero
        assertThat(r.newF and Flags.C).isZero
        assertThat(r.newF and Flags.H).isZero
    }

    @Test
    fun `afterAddWord half-carry boundary 0x0FFF + 0x0001 sets H`() {
        val r = Flags.afterAddWord(0x0FFF, 0x0001, oldF = 0)
        assertThat(r.value).isEqualTo(0x1000)
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterAddWord carry boundary 0xFFFF + 0x0001 wraps and sets C`() {
        val r = Flags.afterAddWord(0xFFFF, 0x0001, oldF = 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.H).isNotZero
    }

    @Test
    fun `afterAddWord preserves S, Z, PV from oldF`() {
        val oldF = Flags.S or Flags.Z or Flags.PV or Flags.C
        val r = Flags.afterAddWord(0x1234, 0x0001, oldF)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.C).isZero
        assertThat(r.newF and Flags.N).isZero
    }

    @Test
    fun `afterAdcWord 0x0001 + 0x0001 + carry=1 = 0x0003, all flag categories computed`() {
        val r = Flags.afterAdcWord(0x0001, 0x0001, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x0003)
        assertThat(r.newF and Flags.S).isZero
        assertThat(r.newF and Flags.Z).isZero
        assertThat(r.newF and Flags.N).isZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterAdcWord half-carry from bit 11`() {
        val r = Flags.afterAdcWord(0x0FFE, 0x0001, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x1000)
        assertThat(r.newF and Flags.H).isNotZero
    }

    @Test
    fun `afterAdcWord overflow at 0x7FFF + 0x0001 sets V and S`() {
        val r = Flags.afterAdcWord(0x7FFF, 0x0001, oldF = 0)
        assertThat(r.value).isEqualTo(0x8000)
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.S).isNotZero
    }

    @Test
    fun `afterAdcWord zero result with carry-out`() {
        val r = Flags.afterAdcWord(0xFFFF, 0x0001, oldF = 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterSbcWord 0x0005 - 0x0002 - borrow=0`() {
        val r = Flags.afterSbcWord(0x0005, 0x0002, oldF = 0)
        assertThat(r.value).isEqualTo(0x0003)
        assertThat(r.newF and Flags.N).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterSbcWord folds in borrow-in`() {
        val r = Flags.afterSbcWord(0x0005, 0x0002, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x0002)
    }

    @Test
    fun `afterSbcWord borrow at 0x0000 - 0x0001 wraps to 0xFFFF, sets C, S, H`() {
        val r = Flags.afterSbcWord(0x0000, 0x0001, oldF = 0)
        assertThat(r.value).isEqualTo(0xFFFF)
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.H).isNotZero
    }

    @Test
    fun `afterSbcWord overflow 0x8000 - 0x0001 sets V`() {
        val r = Flags.afterSbcWord(0x8000, 0x0001, oldF = 0)
        assertThat(r.value).isEqualTo(0x7FFF)
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.S).isZero
    }

    @Test
    fun `afterSbcWord equal operands sets Z`() {
        val r = Flags.afterSbcWord(0x1234, 0x1234, oldF = 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.N).isNotZero
    }
}
