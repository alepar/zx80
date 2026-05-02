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
}
