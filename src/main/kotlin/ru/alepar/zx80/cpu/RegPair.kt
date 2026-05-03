package ru.alepar.zx80.cpu

/**
 * The 16-bit register pairs that opcodes can name. The standard `rr` encoding (BC/DE/HL/SP) is used
 * by LD `rr,nn` and similar opcodes (bits 4-5 of the opcode); see [fromBits].
 *
 * PUSH/POP use a different encoding where bits=11 means **AF** instead of SP. The `AF` value here
 * is only valid for those opcodes; use [fromPushPopBits] to decode it. `fromBits` (the standard
 * encoding) does NOT return `AF`.
 */
enum class RegPair(val mnemonic: String) {
    BC("BC"),
    DE("DE"),
    HL("HL"),
    SP("SP"),
    AF("AF");

    fun read(cpu: Cpu): Int =
        when (this) {
            BC -> cpu.bc
            DE -> cpu.de
            HL -> cpu.hl
            SP -> cpu.sp
            AF -> cpu.af
        }

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFFFF
        when (this) {
            BC -> cpu.bc = v
            DE -> cpu.de = v
            HL -> cpu.hl = v
            SP -> cpu.sp = v
            AF -> cpu.af = v
        }
    }

    companion object {
        /** Standard rr encoding: 00=BC, 01=DE, 10=HL, 11=SP. Used by LD rr,nn etc. */
        fun fromBits(bits: Int): RegPair =
            when (bits and 0x03) {
                0 -> BC
                1 -> DE
                2 -> HL
                3 -> SP
                else -> error("unreachable")
            }

        /** PUSH/POP encoding: 00=BC, 01=DE, 10=HL, 11=AF. */
        fun fromPushPopBits(bits: Int): RegPair =
            when (bits and 0x03) {
                0 -> BC
                1 -> DE
                2 -> HL
                3 -> AF
                else -> error("unreachable")
            }
    }
}
