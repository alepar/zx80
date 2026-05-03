package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.cpu.RegPair

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
        installExtendedLdPair(d)
        installBlockMove(d)
        installBlockCompare(d)
        installSingleIo(d)
        installBlockIo(d)
        installMainTableIoStragglers(d)
    }

    private fun installSingleIo(d: Decoder) {
        for (rrrBits in 0..7) {
            if (rrrBits == 6) {
                d.ed[0x70] = InCFlags
                d.ed[0x71] = OutCZero
            } else {
                val r = Reg.fromBits(rrrBits)
                d.ed[0x40 or (rrrBits shl 3)] = InRC(r)
                d.ed[0x41 or (rrrBits shl 3)] = OutCR(r)
            }
        }
    }

    private fun installBlockIo(d: Decoder) {
        d.ed[0xA2] = Ini
        d.ed[0xAA] = Ind
        d.ed[0xB2] = Inir
        d.ed[0xBA] = Indr
        d.ed[0xA3] = Outi
        d.ed[0xAB] = Outd
        d.ed[0xB3] = Otir
        d.ed[0xBB] = Otdr
    }

    private fun installMainTableIoStragglers(d: Decoder) {
        d.main[0xDB] = InAImm
        d.main[0xD3] = OutImmA
    }

    private fun installBlockCompare(d: Decoder) {
        d.ed[0xA1] = Cpi
        d.ed[0xA9] = Cpd
        d.ed[0xB1] = Cpir
        d.ed[0xB9] = Cpdr
    }

    private fun installBlockMove(d: Decoder) {
        d.ed[0xA0] = Ldi
        d.ed[0xA8] = Ldd
        d.ed[0xB0] = Ldir
        d.ed[0xB8] = Lddr
    }

    private fun installExtendedLdPair(d: Decoder) {
        for (ppBits in 0..3) {
            val pair = RegPair.fromBits(ppBits)
            d.ed[0x43 or (ppBits shl 4)] = LdAddrFromPair(pair)
            d.ed[0x4B or (ppBits shl 4)] = LdPairFromAddr(pair)
        }
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
