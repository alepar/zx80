package ru.alepar.zx80.op.rot

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the rotate Op family into the decoder. Currently: 4 rotate-A opcodes (RLCA, RRCA, RLA,
 * RRA). Phase 2.7 will extend this fragment with the CB-prefixed RLC/RRC/RL/RR/SLA/SRA/SRL r/(HL)
 * variants.
 */
object RotOps {
    fun installInto(d: Decoder) {
        d.main[0x07] = Rlca
        d.main[0x0F] = Rrca
        d.main[0x17] = Rla
        d.main[0x1F] = Rra
    }
}
