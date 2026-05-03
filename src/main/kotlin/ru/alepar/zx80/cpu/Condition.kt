package ru.alepar.zx80.cpu

/**
 * The 8 Z80 condition codes used by JP cc, CALL cc, RET cc. JR cc uses only the first 4 (NZ, Z, NC,
 * C).
 */
enum class Condition(val mnemonic: String) {
    NZ("NZ"),
    Z("Z"),
    NC("NC"),
    C("C"),
    PO("PO"),
    PE("PE"),
    P("P"),
    M("M");

    fun test(cpu: Cpu): Boolean =
        when (this) {
            NZ -> cpu.f and Flags.Z == 0
            Z -> cpu.f and Flags.Z != 0
            NC -> cpu.f and Flags.C == 0
            C -> cpu.f and Flags.C != 0
            PO -> cpu.f and Flags.PV == 0
            PE -> cpu.f and Flags.PV != 0
            P -> cpu.f and Flags.S == 0
            M -> cpu.f and Flags.S != 0
        }

    companion object {
        /** Map Z80 ccc bit pattern (0..7) to Condition. */
        fun fromBits(bits: Int): Condition {
            require(bits in 0..7) { "bits must be in 0..7; got $bits" }
            return entries[bits]
        }
    }
}
