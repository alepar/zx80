package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.AluResult
import ru.alepar.zx80.cpu.Flags

/**
 * The 8 Z80 rotate/shift operations used by the CB-prefixed table. SLL (shift-left-logical) at the
 * bits=6 slot is undocumented but installed in Phase 2.12 (CB 0x30-0x37) since ZEXDOC exercises it.
 */
enum class RotateOp(val mnemonic: String) {
    RLC("RLC"),
    RRC("RRC"),
    RL("RL"),
    RR("RR"),
    SLA("SLA"),
    SRA("SRA"),
    SLL("SLL"),
    SRL("SRL");

    fun apply(value: Int, oldF: Int): AluResult =
        when (this) {
            RLC -> Flags.afterRlc(value)
            RRC -> Flags.afterRrc(value)
            RL -> Flags.afterRl(value, oldF)
            RR -> Flags.afterRr(value, oldF)
            SLA -> Flags.afterSla(value)
            SRA -> Flags.afterSra(value)
            SLL -> Flags.afterSll(value)
            SRL -> Flags.afterSrl(value)
        }

    companion object {
        /**
         * Map CB opcode bits 5-3 (the 'ooo' field) to a RotateOp. Encoding: 0=RLC, 1=RRC, 2=RL,
         * 3=RR, 4=SLA, 5=SRA, 6=SLL (undocumented but installed in Phase 2.12), 7=SRL.
         */
        fun fromBits(bits: Int): RotateOp =
            when (bits and 0x07) {
                0 -> RLC
                1 -> RRC
                2 -> RL
                3 -> RR
                4 -> SLA
                5 -> SRA
                6 -> SLL
                7 -> SRL
                else -> error("unreachable")
            }
    }
}
