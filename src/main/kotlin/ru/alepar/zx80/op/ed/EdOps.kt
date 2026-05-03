package ru.alepar.zx80.op.ed

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.cpu.RegPair
import ru.alepar.zx80.op.misc.Im

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
        installAlternateOpcodes(d)
        installEdNopFallback(d)
    }

    /**
     * Installs Z80-standard alternate ED-prefix slots for NEG/RETN/RETI/IM. Real Z80 hardware
     * decodes `010xxx100` as NEG (8 slots), `010xx0101` as RETN (4 slots), `010xx1101` as RETI (4
     * slots), and specific patterns for IM 0/1/2 with multiple aliases each. We installed the
     * canonical slots in earlier WUs; Phase G adds the rest. Must run BEFORE installEdNopFallback
     * so EdNop doesn't overwrite the aliases.
     */
    private fun installAlternateOpcodes(d: Decoder) {
        // NEG aliases (besides 0x44)
        for (slot in listOf(0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C)) {
            d.ed[slot] = Neg
        }
        // RETN aliases (besides 0x45)
        for (slot in listOf(0x55, 0x65, 0x75)) {
            d.ed[slot] = Retn
        }
        // RETI aliases (besides 0x4D)
        for (slot in listOf(0x5D, 0x6D, 0x7D)) {
            d.ed[slot] = Reti
        }
        // IM 0 aliases (besides 0x46)
        for (slot in listOf(0x4E, 0x66, 0x6E)) {
            d.ed[slot] = Im(0)
        }
        // IM 1 alias (besides 0x56)
        d.ed[0x76] = Im(1)
        // IM 2 alias (besides 0x5E)
        d.ed[0x7E] = Im(2)
    }

    /**
     * Phase F: fill any still-null ED slot with `EdNop`. Real Z80 hardware treats unmapped
     * ED-prefixed opcodes (ED 0x00-0x3F, parts of ED 0x80-0x9F, ED 0xC0-0xFF) as 8-cycle 2-byte
     * NOPs. Without this fallback the dispatcher would crash on any FUSE/ZEXDOC test that hits an
     * unmapped slot.
     */
    private fun installEdNopFallback(d: Decoder) {
        for (i in 0..255) {
            if (d.ed[i] == null) {
                d.ed[i] = EdNop
            }
        }
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
