package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the remaining ED-prefixed Op family into decoder.ed (and the two main-table I/O
 * stragglers IN A,(n)/OUT (n),A into decoder.main). Filled in by WUs 2.10-2 through 2.10-6.
 *
 * (IM 0/1/2 was installed in Phase 2.1a-3 by MiscOps. ADC HL,rr and SBC HL,rr were installed in
 * Phase 2.3 by AluOps.)
 */
object EdOps {
    fun installInto(d: Decoder) {
        // Filled in by WUs 2.10-2 through 2.10-6.
    }
}
