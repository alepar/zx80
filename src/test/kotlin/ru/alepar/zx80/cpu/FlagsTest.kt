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

    @Test
    fun `afterRotateA sets C from newC, clears H and N, preserves S Z PV`() {
        val oldF = Flags.S or Flags.Z or Flags.PV or Flags.N or Flags.H or Flags.C
        val r = Flags.afterRotateA(rotated = 0x42, newC = false, oldF = oldF)
        assertThat(r.value).isEqualTo(0x42)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.N).isZero
        assertThat(r.newF and Flags.H).isZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterRotateA sets C when newC is true`() {
        val r = Flags.afterRotateA(rotated = 0x80, newC = true, oldF = 0)
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterRotateA masks rotated to 8 bits`() {
        val r = Flags.afterRotateA(rotated = 0x1FF, newC = false, oldF = 0)
        assertThat(r.value).isEqualTo(0xFF)
    }

    @Test
    fun `afterCpl xors A with 0xFF, sets H and N, preserves S Z PV C`() {
        val oldF = Flags.S or Flags.Z or Flags.PV or Flags.C
        val r = Flags.afterCpl(0x12, oldF)
        assertThat(r.value).isEqualTo(0xED)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.PV).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.N).isNotZero
    }

    @Test
    fun `afterCpl 0x00 gives 0xFF`() {
        val r = Flags.afterCpl(0x00, 0)
        assertThat(r.value).isEqualTo(0xFF)
    }

    @Test
    fun `afterCpl 0xFF gives 0x00`() {
        val r = Flags.afterCpl(0xFF, 0)
        assertThat(r.value).isZero
    }

    @Test
    fun `afterScf sets C, clears H and N, preserves S Z PV`() {
        val oldF = Flags.S or Flags.Z or Flags.PV or Flags.H or Flags.N
        val newF = Flags.afterScf(oldF)
        assertThat(newF and Flags.S).isNotZero
        assertThat(newF and Flags.Z).isNotZero
        assertThat(newF and Flags.PV).isNotZero
        assertThat(newF and Flags.H).isZero
        assertThat(newF and Flags.N).isZero
        assertThat(newF and Flags.C).isNotZero
    }

    @Test
    fun `afterCcf toggles C, sets H to oldC, clears N, preserves S Z PV`() {
        var oldF = Flags.C or Flags.S
        var newF = Flags.afterCcf(oldF)
        assertThat(newF and Flags.C).isZero
        assertThat(newF and Flags.H).isNotZero
        assertThat(newF and Flags.N).isZero
        assertThat(newF and Flags.S).isNotZero

        oldF = Flags.S or Flags.Z
        newF = Flags.afterCcf(oldF)
        assertThat(newF and Flags.C).isNotZero
        assertThat(newF and Flags.H).isZero
        assertThat(newF and Flags.S).isNotZero
        assertThat(newF and Flags.Z).isNotZero
    }

    @Test
    fun `afterDaa after ADD 0x09 plus 0x01 = 0x0A, no flags set, adjusts to 0x10 with H`() {
        val r = Flags.afterDaa(0x0A, oldF = 0)
        assertThat(r.value).isEqualTo(0x10)
        assertThat(r.newF and Flags.H).isNotZero
        assertThat(r.newF and Flags.C).isZero
        assertThat(r.newF and Flags.N).isZero
    }

    @Test
    fun `afterDaa after ADD 0x99 plus 0x01 = 0x9A, adjusts to 0x00 with C and Z`() {
        val r = Flags.afterDaa(0x9A, oldF = 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterDaa A=0x00 N=0 no flags is no-op (Z set)`() {
        val r = Flags.afterDaa(0x00, oldF = 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterDaa A=0x00 N=1 C=1 (after sub borrow) gives 0xA0 with S and C`() {
        val r = Flags.afterDaa(0x00, oldF = Flags.N or Flags.C)
        assertThat(r.value).isEqualTo(0xA0)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.N).isNotZero
    }

    @Test
    fun `afterDaa preserves N flag`() {
        var r = Flags.afterDaa(0x12, oldF = 0)
        assertThat(r.newF and Flags.N).isZero

        r = Flags.afterDaa(0x12, oldF = Flags.N)
        assertThat(r.newF and Flags.N).isNotZero
    }

    @Test
    fun `afterDaa parity flag set for even bit count in result`() {
        val r = Flags.afterDaa(0x33, oldF = 0)
        assertThat(r.value).isEqualTo(0x33)
        assertThat(r.newF and Flags.PV).isNotZero
    }

    @Test
    fun `afterDaa H flag computed from result-vs-input bit 4 difference`() {
        val r = Flags.afterDaa(0x0A, oldF = 0)
        assertThat(r.newF and Flags.H).isNotZero
    }

    @Test
    fun `afterDaa S flag set when result bit 7 is set`() {
        var r = Flags.afterDaa(0x9A, oldF = 0)
        assertThat(r.newF and Flags.S).isZero

        r = Flags.afterDaa(0x82, oldF = 0)
        assertThat(r.newF and Flags.S).isNotZero
    }

    @Test
    fun `afterRlc 0x80 rotates to 0x01 with C set, Z clear`() {
        val r = Flags.afterRlc(0x80)
        assertThat(r.value).isEqualTo(0x01)
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.Z).isZero
        assertThat(r.newF and Flags.S).isZero
        assertThat(r.newF and Flags.H).isZero
        assertThat(r.newF and Flags.N).isZero
        assertThat(r.newF and Flags.PV).isZero
    }

    @Test
    fun `afterRlc 0x00 stays 0x00 with Z set, no C`() {
        val r = Flags.afterRlc(0x00)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isZero
        assertThat(r.newF and Flags.PV).isNotZero
    }

    @Test
    fun `afterRlc 0x55 rotates to 0xAA, S set`() {
        val r = Flags.afterRlc(0x55)
        assertThat(r.value).isEqualTo(0xAA)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterRrc 0x01 rotates to 0x80 with C set, S set`() {
        val r = Flags.afterRrc(0x01)
        assertThat(r.value).isEqualTo(0x80)
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.Z).isZero
    }

    @Test
    fun `afterRrc 0x00 stays 0x00 with Z set`() {
        val r = Flags.afterRrc(0x00)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterRrc 0x02 gives 0x01, no C`() {
        val r = Flags.afterRrc(0x02)
        assertThat(r.value).isEqualTo(0x01)
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterRl 0x80 with C=1 gives 0x01 with C set`() {
        val r = Flags.afterRl(0x80, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x01)
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterRl 0x80 with C=0 gives 0x00 with Z and C set`() {
        val r = Flags.afterRl(0x80, oldF = 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterRl 0x40 with C=0 gives 0x80, S set`() {
        val r = Flags.afterRl(0x40, oldF = 0)
        assertThat(r.value).isEqualTo(0x80)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterRr 0x01 with C=1 gives 0x80 with C set`() {
        val r = Flags.afterRr(0x01, oldF = Flags.C)
        assertThat(r.value).isEqualTo(0x80)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterRr 0x01 with C=0 gives 0x00 with Z and C set`() {
        val r = Flags.afterRr(0x01, oldF = 0)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterRr 0x02 with C=0 gives 0x01, no C`() {
        val r = Flags.afterRr(0x02, oldF = 0)
        assertThat(r.value).isEqualTo(0x01)
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterSla 0x80 gives 0x00 with Z and C set`() {
        val r = Flags.afterSla(0x80)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterSla 0x40 gives 0x80, S set, no C`() {
        val r = Flags.afterSla(0x40)
        assertThat(r.value).isEqualTo(0x80)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterSla 0xFF gives 0xFE, S and C set`() {
        val r = Flags.afterSla(0xFF)
        assertThat(r.value).isEqualTo(0xFE)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterSra 0x80 gives 0xC0 with sign bit preserved`() {
        val r = Flags.afterSra(0x80)
        assertThat(r.value).isEqualTo(0xC0)
        assertThat(r.newF and Flags.S).isNotZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterSra 0x01 gives 0x00 with Z and C set`() {
        val r = Flags.afterSra(0x01)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterSra 0x42 gives 0x21, no C, no sign change`() {
        val r = Flags.afterSra(0x42)
        assertThat(r.value).isEqualTo(0x21)
        assertThat(r.newF and Flags.S).isZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterSrl 0x80 gives 0x40 no sign preservation`() {
        val r = Flags.afterSrl(0x80)
        assertThat(r.value).isEqualTo(0x40)
        assertThat(r.newF and Flags.S).isZero
        assertThat(r.newF and Flags.C).isZero
    }

    @Test
    fun `afterSrl 0x01 gives 0x00 with Z and C set`() {
        val r = Flags.afterSrl(0x01)
        assertThat(r.value).isZero
        assertThat(r.newF and Flags.Z).isNotZero
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterSrl 0xFF gives 0x7F, no sign, C set`() {
        val r = Flags.afterSrl(0xFF)
        assertThat(r.value).isEqualTo(0x7F)
        assertThat(r.newF and Flags.S).isZero
        assertThat(r.newF and Flags.C).isNotZero
    }

    @Test
    fun `afterSll shifts left and forces bit 0 to 1, ejected bit 7 sets C`() {
        val r = Flags.afterSll(0x55)
        // 0x55 = 01010101 -> shift left = 10101010, set bit 0 -> 10101011 = 0xAB
        // ejected bit 7 of 0x55 = 0 -> C clear
        assertThat(r.value).isEqualTo(0xAB)
        assertThat(r.newF and Flags.C).isZero
        assertThat(r.newF and Flags.S).isNotZero // bit 7 of 0xAB = 1
        assertThat(r.newF and Flags.Z).isZero
        assertThat(r.newF and Flags.H).isZero
        assertThat(r.newF and Flags.N).isZero
    }

    @Test
    fun `afterSll on 0x80 shifts to 0x01 and sets C`() {
        val r = Flags.afterSll(0x80)
        // ejected bit 7 = 1 -> C set; result = 0x00 shifted left + 1 = 0x01
        assertThat(r.value).isEqualTo(0x01)
        assertThat(r.newF and Flags.C).isNotZero
        assertThat(r.newF and Flags.S).isZero
        assertThat(r.newF and Flags.Z).isZero
    }

    @Test
    fun `afterSll on 0x00 yields 0x01, no flags except parity`() {
        val r = Flags.afterSll(0x00)
        assertThat(r.value).isEqualTo(0x01)
        assertThat(r.newF and Flags.C).isZero
        assertThat(r.newF and Flags.Z).isZero // 0x01 != 0
    }
}
