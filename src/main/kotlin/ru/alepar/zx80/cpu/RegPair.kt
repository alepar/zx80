package ru.alepar.zx80.cpu

/**
 * The four 16-bit register pairs that LD `rr,nn` and similar opcodes can name. Uses the BC/DE/HL/SP
 * encoding (bits 4-5 of the opcode).
 *
 * Note: PUSH/POP use a different encoding where bits=11 means AF instead of SP. That's a separate
 * concern and lives in its own factory (`fromPushPopBits`) when those opcodes land.
 */
enum class RegPair(val mnemonic: String) {
    BC("BC"),
    DE("DE"),
    HL("HL"),
    SP("SP");

    fun read(cpu: Cpu): Int =
        when (this) {
            BC -> cpu.bc
            DE -> cpu.de
            HL -> cpu.hl
            SP -> cpu.sp
        }

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFFFF
        when (this) {
            BC -> cpu.bc = v
            DE -> cpu.de = v
            HL -> cpu.hl = v
            SP -> cpu.sp = v
        }
    }

    companion object {
        /** Map a Z80 register-pair bit pattern (low 2 bits used) to [RegPair]. */
        fun fromBits(bits: Int): RegPair =
            when (bits and 0x03) {
                0 -> BC
                1 -> DE
                2 -> HL
                3 -> SP
                else -> error("unreachable")
            }
    }
}
