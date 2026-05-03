package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the remaining ED-prefixed Op family into decoder.ed (and the two main-table I/O
 * stragglers IN A,(n)/OUT (n),A into decoder.main).
 *
 * (IM 0/1/2 was installed in Phase 2.1a-3 by MiscOps. ADC HL,rr and SBC HL,rr were installed in
 * Phase 2.3 by AluOps.)
 */
object EdOps {
    fun installInto(d: Decoder) {
        installRegisterTransfers(d)
        installNeg(d)
        installReturns(d)
        installRrdRld(d)
    }

    private fun installRegisterTransfers(d: Decoder) {
        d.ed[0x47] = LdIA
        d.ed[0x4F] = LdRA
        d.ed[0x57] = LdAI
        d.ed[0x5F] = LdAR
    }

    private fun installNeg(d: Decoder) {
        d.ed[0x44] = Neg
    }

    private fun installReturns(d: Decoder) {
        d.ed[0x45] = Retn
        d.ed[0x4D] = Reti
    }

    private fun installRrdRld(d: Decoder) {
        d.ed[0x67] = Rrd
        d.ed[0x6F] = Rld
    }
}
