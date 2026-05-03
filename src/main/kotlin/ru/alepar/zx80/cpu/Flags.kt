package ru.alepar.zx80.cpu

/**
 * Z80 flag-bit constants. The `f` register holds (from MSB to LSB):
 *
 * S Z X H Y P/V N C
 *
 * X (bit 5) and Y (bit 3) are undocumented copies of result bits — we track them only because the
 * FUSE test suite checks them; they don't influence any documented branch.
 */
object Flags {
    const val C = 0x01 // Carry
    const val N = 0x02 // Add/Subtract
    const val PV = 0x04 // Parity / Overflow
    const val Y = 0x08 // Undocumented (bit 3 of result)
    const val H = 0x10 // Half-carry
    const val X = 0x20 // Undocumented (bit 5 of result)
    const val Z = 0x40 // Zero
    const val S = 0x80 // Sign

    /** True iff `value` (low 8 bits only) has even parity (even number of 1 bits). */
    fun parity(value: Int): Boolean = Integer.bitCount(value and 0xFF) and 1 == 0

    /**
     * ADD/ADC: a + b + carry. Both operands treated as unsigned 8-bit. Carry param must be 0 or 1.
     *
     * Flag rules:
     * - S = bit 7 of result.
     * - Z = result == 0.
     * - H = carry from bit 3 to bit 4 (i.e. (a & 0x0F) + (b & 0x0F) + carry > 0x0F).
     * - P/V = signed overflow (both operands same sign, result different sign).
     * - N = 0 (ADD clears N).
     * - C = carry from bit 7 (sum > 0xFF).
     */
    fun afterAdd(a: Int, b: Int, carry: Int): AluResult {
        val sum = a + b + carry
        val value = sum and 0xFF
        var f = 0
        if (value == 0) f = f or Z
        if (value and 0x80 != 0) f = f or S
        if ((a and 0x0F) + (b and 0x0F) + carry > 0x0F) f = f or H
        // Overflow: both operands have same sign bit, result has different sign bit
        if ((a xor b) and 0x80 == 0 && (a xor value) and 0x80 != 0) f = f or PV
        if (sum > 0xFF) f = f or C
        f = f or (value and 0x28)
        return AluResult(value, f)
    }

    /**
     * SUB/SBC/CP: a - b - borrow. Both operands treated as unsigned 8-bit. Borrow param must be 0
     * or 1.
     *
     * Flag rules:
     * - S = bit 7 of result.
     * - Z = result == 0.
     * - H = borrow from bit 4 (i.e. (a & 0x0F) - (b & 0x0F) - borrow < 0).
     * - P/V = signed overflow (operands different signs, result has same sign as b).
     * - N = 1 (SUB sets N).
     * - C = borrow from bit 8 (a - b - borrow < 0).
     */
    fun afterSub(a: Int, b: Int, borrow: Int): AluResult {
        val diff = a - b - borrow
        val value = diff and 0xFF
        var f = N
        if (value == 0) f = f or Z
        if (value and 0x80 != 0) f = f or S
        if ((a and 0x0F) - (b and 0x0F) - borrow < 0) f = f or H
        // Overflow: operands have different sign bits, and result has different sign bit from a
        if ((a xor b) and 0x80 != 0 && (a xor value) and 0x80 != 0) f = f or PV
        if (diff < 0) f = f or C
        f = f or (value and 0x28)
        return AluResult(value, f)
    }

    /**
     * AND a, b. Result is bitwise AND of the low 8 bits of each. Flags: S = bit 7 of result, Z =
     * result == 0, H = 1, P/V = parity, N = 0, C = 0.
     */
    fun afterAnd(a: Int, b: Int): AluResult {
        val value = (a and b) and 0xFF
        var f = H
        if (value == 0) f = f or Z
        if (value and 0x80 != 0) f = f or S
        if (parity(value)) f = f or PV
        f = f or (value and 0x28)
        return AluResult(value, f)
    }

    /**
     * OR a, b. Result is bitwise OR. Flags: S = bit 7 of result, Z = result == 0, H = 0, P/V =
     * parity, N = 0, C = 0.
     */
    fun afterOr(a: Int, b: Int): AluResult {
        val value = (a or b) and 0xFF
        var f = 0
        if (value == 0) f = f or Z
        if (value and 0x80 != 0) f = f or S
        if (parity(value)) f = f or PV
        f = f or (value and 0x28)
        return AluResult(value, f)
    }

    /**
     * XOR a, b. Result is bitwise XOR. Flags: S = bit 7 of result, Z = result == 0, H = 0, P/V =
     * parity, N = 0, C = 0.
     */
    fun afterXor(a: Int, b: Int): AluResult {
        val value = (a xor b) and 0xFF
        var f = 0
        if (value == 0) f = f or Z
        if (value and 0x80 != 0) f = f or S
        if (parity(value)) f = f or PV
        f = f or (value and 0x28)
        return AluResult(value, f)
    }

    /**
     * INC value (8-bit). Returns (value+1 & 0xFF, F).
     *
     * Flag rules:
     * - S = bit 7 of result.
     * - Z = result == 0.
     * - H = carry from bit 3 (i.e. (value & 0x0F) + 1 > 0x0F).
     * - P/V = overflow (value was 0x7F).
     * - N = 0.
     * - C = preserved from oldF.
     */
    fun afterInc(value: Int, oldF: Int): AluResult {
        val v8 = value and 0xFF
        val result = (v8 + 1) and 0xFF
        var f = oldF and C // preserve C only
        if (result == 0) f = f or Z
        if (result and 0x80 != 0) f = f or S
        if ((v8 and 0x0F) + 1 > 0x0F) f = f or H
        if (v8 == 0x7F) f = f or PV
        f = f or (result and 0x28)
        return AluResult(result, f)
    }

    /**
     * DEC value (8-bit). Returns (value-1 & 0xFF, F).
     *
     * Flag rules:
     * - S = bit 7 of result.
     * - Z = result == 0.
     * - H = borrow from bit 4 (i.e. (value & 0x0F) == 0).
     * - P/V = overflow (value was 0x80).
     * - N = 1.
     * - C = preserved from oldF.
     */
    fun afterDec(value: Int, oldF: Int): AluResult {
        val v8 = value and 0xFF
        val result = (v8 - 1) and 0xFF
        var f = (oldF and C) or N // preserve C, set N
        if (result == 0) f = f or Z
        if (result and 0x80 != 0) f = f or S
        if (v8 and 0x0F == 0) f = f or H // borrow when low nibble was 0
        if (v8 == 0x80) f = f or PV
        f = f or (result and 0x28)
        return AluResult(result, f)
    }

    /**
     * 16-bit ADD: a + b. Only H, N, C are computed; S, Z, P/V are PRESERVED from oldF. (Unique to
     * ADD HL,rr; ADC/SBC HL,rr compute all flags.)
     * - H = carry from bit 11 (i.e. (a & 0x0FFF) + (b & 0x0FFF) > 0x0FFF).
     * - N = 0.
     * - C = sum > 0xFFFF.
     */
    fun afterAddWord(a: Int, b: Int, oldF: Int): AluResult {
        val sum = a + b
        val value = sum and 0xFFFF
        var f = oldF and (S or Z or PV) // preserve these
        if ((a and 0x0FFF) + (b and 0x0FFF) > 0x0FFF) f = f or H
        if (sum > 0xFFFF) f = f or C
        f = f or ((value ushr 8) and 0x28)
        return AluResult(value, f)
    }

    /**
     * 16-bit ADC: a + b + carry. ALL flags computed.
     * - S = bit 15 of result.
     * - Z = result == 0.
     * - H = carry from bit 11 (with carry-in folded).
     * - P/V = signed overflow at bit 15.
     * - N = 0.
     * - C = sum > 0xFFFF.
     */
    fun afterAdcWord(a: Int, b: Int, oldF: Int): AluResult {
        val carry = if (oldF and C != 0) 1 else 0
        val sum = a + b + carry
        val value = sum and 0xFFFF
        var f = 0
        if (value == 0) f = f or Z
        if (value and 0x8000 != 0) f = f or S
        if ((a and 0x0FFF) + (b and 0x0FFF) + carry > 0x0FFF) f = f or H
        if ((a xor b) and 0x8000 == 0 && (a xor value) and 0x8000 != 0) f = f or PV
        if (sum > 0xFFFF) f = f or C
        f = f or ((value ushr 8) and 0x28)
        return AluResult(value, f)
    }

    /**
     * 16-bit SBC: a - b - borrow. ALL flags computed.
     * - S = bit 15 of result.
     * - Z = result == 0.
     * - H = borrow from bit 12 (with borrow-in folded).
     * - P/V = signed overflow.
     * - N = 1.
     * - C = a - b - borrow < 0.
     */
    fun afterSbcWord(a: Int, b: Int, oldF: Int): AluResult {
        val borrow = if (oldF and C != 0) 1 else 0
        val diff = a - b - borrow
        val value = diff and 0xFFFF
        var f = N
        if (value == 0) f = f or Z
        if (value and 0x8000 != 0) f = f or S
        if ((a and 0x0FFF) - (b and 0x0FFF) - borrow < 0) f = f or H
        if ((a xor b) and 0x8000 != 0 && (a xor value) and 0x8000 != 0) f = f or PV
        if (diff < 0) f = f or C
        f = f or ((value ushr 8) and 0x28)
        return AluResult(value, f)
    }

    /**
     * Common flag rules for the 4 rotate-A opcodes (RLCA, RRCA, RLA, RRA): C = newC (the bit
     * shifted out); H = 0; N = 0; S/Z/PV preserved from oldF.
     *
     * Caller computes the rotated value and newC; this helper packages the result + new F.
     */
    fun afterRotateA(rotated: Int, newC: Boolean, oldF: Int): AluResult {
        val value = rotated and 0xFF
        var f = oldF and (S or Z or PV)
        if (newC) f = f or C
        f = f or (value and 0x28)
        return AluResult(value, f)
    }

    /** CPL: A = A xor 0xFF. H = 1, N = 1; S/Z/PV/C preserved from oldF. */
    fun afterCpl(a: Int, oldF: Int): AluResult {
        val value = (a and 0xFF) xor 0xFF
        val f = (oldF and (S or Z or PV or C)) or H or N or (value and 0x28)
        return AluResult(value, f)
    }

    /**
     * SCF: set carry flag. C=1; H=0; N=0; S/Z/PV preserved. Returns just the new F (A is
     * unchanged).
     */
    fun afterScf(oldF: Int): Int = (oldF and (S or Z or PV)) or C

    /**
     * CCF: complement carry flag. C=!oldC; H=oldC; N=0; S/Z/PV preserved. Returns just the new F.
     */
    fun afterCcf(oldF: Int): Int {
        val oldC = oldF and C
        var f = oldF and (S or Z or PV)
        if (oldC == 0) f = f or C // toggle to 1
        if (oldC != 0) f = f or H // H gets oldC
        return f
    }

    /**
     * BCD adjust accumulator after a previous arithmetic operation. Behavior depends on N, H, C
     * flags from the previous op.
     *
     * Algorithm (standard table-based):
     * - if N=0 (after add): if H or low nibble > 9: correction |= 0x06; if C or A > 0x99:
     *   correction |= 0x60, set new C; A += correction.
     * - else (after sub): if H: correction |= 0x06; if C: correction |= 0x60; A -= correction.
     *
     * Flags after:
     * - S = bit 7 of result.
     * - Z = result == 0.
     * - H = bit-4 differs between A and result (covers add and sub paths).
     * - P/V = parity of result.
     * - N = preserved from oldF.
     * - C = potentially set if high-nibble correction was applied.
     */
    /**
     * RLC: rotate value left circular. Bit 7 → C and → bit 0. Flag rules: S/Z from result; H=0;
     * PV=parity; N=0; C from old bit 7.
     */
    fun afterRlc(value: Int): AluResult {
        val v = value and 0xFF
        val newC = (v and 0x80) != 0
        val result = ((v shl 1) or (if (newC) 1 else 0)) and 0xFF
        return AluResult(result, computeRotateShiftFlags(result, newC))
    }

    /** RRC: rotate value right circular. Bit 0 → C and → bit 7. */
    fun afterRrc(value: Int): AluResult {
        val v = value and 0xFF
        val newC = (v and 0x01) != 0
        val result = ((v ushr 1) or (if (newC) 0x80 else 0)) and 0xFF
        return AluResult(result, computeRotateShiftFlags(result, newC))
    }

    /** RL: rotate left through carry. Old C → new bit 0; old bit 7 → new C. */
    fun afterRl(value: Int, oldF: Int): AluResult {
        val v = value and 0xFF
        val oldC = if (oldF and C != 0) 1 else 0
        val newC = (v and 0x80) != 0
        val result = ((v shl 1) or oldC) and 0xFF
        return AluResult(result, computeRotateShiftFlags(result, newC))
    }

    /** RR: rotate right through carry. Old C → new bit 7; old bit 0 → new C. */
    fun afterRr(value: Int, oldF: Int): AluResult {
        val v = value and 0xFF
        val oldC = if (oldF and C != 0) 0x80 else 0
        val newC = (v and 0x01) != 0
        val result = ((v ushr 1) or oldC) and 0xFF
        return AluResult(result, computeRotateShiftFlags(result, newC))
    }

    /** SLA: shift left arithmetic. Bit 0 always 0; bit 7 → C. */
    fun afterSla(value: Int): AluResult {
        val v = value and 0xFF
        val newC = (v and 0x80) != 0
        val result = (v shl 1) and 0xFF
        return AluResult(result, computeRotateShiftFlags(result, newC))
    }

    /** SRA: shift right arithmetic. Bit 7 preserved (sign extend); bit 0 → C. */
    fun afterSra(value: Int): AluResult {
        val v = value and 0xFF
        val newC = (v and 0x01) != 0
        val result = ((v ushr 1) or (v and 0x80)) and 0xFF
        return AluResult(result, computeRotateShiftFlags(result, newC))
    }

    /** SRL: shift right logical. Bit 7 always 0; bit 0 → C. */
    fun afterSrl(value: Int): AluResult {
        val v = value and 0xFF
        val newC = (v and 0x01) != 0
        val result = (v ushr 1) and 0xFF
        return AluResult(result, computeRotateShiftFlags(result, newC))
    }

    /**
     * SLL: shift left logical, undocumented. Bit 0 always 1; bit 7 → C. Z80 quirk: this is
     * sometimes called "SL1" or "SLIA" in disassemblers since SLA already shifts left and SLL
     * forces bit 0 to 1 instead of 0.
     */
    fun afterSll(value: Int): AluResult {
        val v = value and 0xFF
        val newC = (v and 0x80) != 0
        val result = ((v shl 1) or 0x01) and 0xFF
        return AluResult(result, computeRotateShiftFlags(result, newC))
    }

    private fun computeRotateShiftFlags(result: Int, newC: Boolean): Int {
        var f = 0
        if (result == 0) f = f or Z
        if (result and 0x80 != 0) f = f or S
        if (parity(result)) f = f or PV
        if (newC) f = f or C
        f = f or (result and 0x28)
        return f
    }

    fun afterDaa(a: Int, oldF: Int): AluResult {
        val n = oldF and N != 0
        val cFlag = oldF and C != 0
        val hFlag = oldF and H != 0
        var correction = 0
        var newC = cFlag
        if (hFlag || (a and 0x0F) > 9) correction = correction or 0x06
        if (cFlag || a > 0x99) {
            correction = correction or 0x60
            newC = true
        }
        val result = (if (n) a - correction else a + correction) and 0xFF
        var f = oldF and N // preserve N
        if (result == 0) f = f or Z
        if (result and 0x80 != 0) f = f or S
        if (parity(result)) f = f or PV
        if (newC) f = f or C
        if ((a xor result) and 0x10 != 0) f = f or H
        f = f or (result and 0x28)
        return AluResult(result, f)
    }
}
