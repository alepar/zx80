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
        return AluResult(result, f)
    }
}
