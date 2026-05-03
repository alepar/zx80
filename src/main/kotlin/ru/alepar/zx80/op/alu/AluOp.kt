package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.AluResult
import ru.alepar.zx80.cpu.Flags

/**
 * The 8 Z80 ALU operations on the accumulator. `apply(a, b, oldF)` returns the result + new F
 * register. `updatesA` is false only for CP (which computes flags as if it were SUB but does not
 * write A).
 */
enum class AluOp(val mnemonic: String, val updatesA: Boolean) {
    ADD("ADD", true),
    ADC("ADC", true),
    SUB("SUB", true),
    SBC("SBC", true),
    AND("AND", true),
    OR("OR", true),
    XOR("XOR", true),
    CP("CP", false);

    fun apply(a: Int, b: Int, oldF: Int): AluResult =
        when (this) {
            ADD -> Flags.afterAdd(a, b, 0)
            ADC -> Flags.afterAdd(a, b, if (oldF and Flags.C != 0) 1 else 0)
            SUB -> Flags.afterSub(a, b, 0)
            CP -> Flags.afterCp(a, b, 0)
            SBC -> Flags.afterSub(a, b, if (oldF and Flags.C != 0) 1 else 0)
            AND -> Flags.afterAnd(a, b)
            OR -> Flags.afterOr(a, b)
            XOR -> Flags.afterXor(a, b)
        }

    companion object {
        /**
         * Map Z80 opcode bits 5-3 (the 'ooo' field of `10 ooo rrr` ALU opcodes) to an AluOp.
         *
         * Encoding: 000=ADD 001=ADC 010=SUB 011=SBC 100=AND 101=XOR 110=OR 111=CP.
         */
        fun fromBits(bits: Int): AluOp =
            when (bits and 0x07) {
                0 -> ADD
                1 -> ADC
                2 -> SUB
                3 -> SBC
                4 -> AND
                5 -> XOR
                6 -> OR
                7 -> CP
                else -> error("unreachable")
            }
    }
}
