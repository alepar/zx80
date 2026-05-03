package ru.alepar.zx80.cpu

/**
 * The seven 8-bit Z80 registers that the `r` field of a typical opcode can name. The Z80 encoding
 * bit pattern `110` (which means `(HL)`, a memory access) is intentionally NOT in this enum —
 * instructions with an `(HL)` operand are modelled as separate Op classes so that this enum stays
 * purely register-side.
 */
enum class Reg(val mnemonic: String) {
    B("B"),
    C("C"),
    D("D"),
    E("E"),
    H("H"),
    L("L"),
    A("A");

    fun read(cpu: Cpu): Int =
        when (this) {
            B -> cpu.b
            C -> cpu.c
            D -> cpu.d
            E -> cpu.e
            H -> cpu.h
            L -> cpu.l
            A -> cpu.a
        }

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFF
        when (this) {
            B -> cpu.b = v
            C -> cpu.c = v
            D -> cpu.d = v
            E -> cpu.e = v
            H -> cpu.h = v
            L -> cpu.l = v
            A -> cpu.a = v
        }
    }

    companion object {
        /**
         * Map a Z80 r-field bit pattern (0..7) to the corresponding [Reg]. Bits=6 means `(HL)` — a
         * memory access, not a register — and is rejected. Callers handling `(HL)`-bearing opcodes
         * branch on bits before calling this.
         */
        fun fromBits(bits: Int): Reg {
            require(bits in 0..7) { "bits must be in 0..7; got $bits" }
            require(bits != 6) { "bits=6 is (HL), not a register" }
            return when (bits) {
                0 -> B
                1 -> C
                2 -> D
                3 -> E
                4 -> H
                5 -> L
                7 -> A
                else -> error("unreachable")
            }
        }
    }
}
