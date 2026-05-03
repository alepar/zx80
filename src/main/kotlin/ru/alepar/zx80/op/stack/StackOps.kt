package ru.alepar.zx80.op.stack

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.RegPair

/**
 * Registers the stack Op family (PUSH rr, POP rr) into the decoder. Called by
 * [ru.alepar.zx80.op.OpTableBuilder]. PUSH/POP use the AF-at-bits-3 encoding
 * (RegPair.fromPushPopBits), distinct from the SP-at-bits-3 encoding used by LD rr,nn etc.
 */
object StackOps {
    fun installInto(d: Decoder) {
        // PUSH rr — 11 qq 0101 → C5, D5, E5, F5
        // POP  rr — 11 qq 0001 → C1, D1, E1, F1
        for (qqBits in 0..3) {
            val pair = RegPair.fromPushPopBits(qqBits)
            d.main[0xC5 or (qqBits shl 4)] = PushPair(src = pair)
            d.main[0xC1 or (qqBits shl 4)] = PopPair(dst = pair)
        }
    }
}
