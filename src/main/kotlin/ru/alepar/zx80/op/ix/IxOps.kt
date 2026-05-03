package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.IndexHalfReg
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.op.Op
import ru.alepar.zx80.op.alu.AluOp

/**
 * Registers the DD/FD-prefixed (IX/IY) Op family into decoder.dd and decoder.fd. Also registers the
 * LD SP,HL straggler at decoder.main[0xF9] (it sits naturally alongside LD SP,IX and LD SP,IY).
 */
object IxOps {
    fun installInto(d: Decoder) {
        installLdSpHlStraggler(d)
        installPairTouching(d)
        installIndexedLd(d)
        installIndexedLdImm(d)
        installIndexedAlu(d)
        installIndexedIncDec(d)
        installJpIx(d)
        installAluAHalves(d)
        installIncDecHalves(d)
        installLdHalfImm(d)
        installLdRegRegPrefixed(d)
    }

    private fun installLdRegRegPrefixed(d: Decoder) {
        // Precompute the unique LdRegRegPrefixed instances (no-half-touching patterns)
        // and reuse across both prefixes.
        val sharedNoHalf =
            Array(8) { dstBits ->
                Array<Op?>(8) { srcBits ->
                    if (dstBits in setOf(4, 5) || srcBits in setOf(4, 5)) {
                        null // half-touching — handled per-prefix below
                    } else if (dstBits == 6 || srcBits == 6) {
                        null // (IX+d) — already installed by Phase 2.8
                    } else {
                        LdRegRegPrefixed(src = Reg.fromBits(srcBits), dst = Reg.fromBits(dstBits))
                    }
                }
            }

        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            val high = if (idx == IndexReg.IX) IndexHalfReg.IXH else IndexHalfReg.IYH
            val low = if (idx == IndexReg.IX) IndexHalfReg.IXL else IndexHalfReg.IYL
            for (dstBits in 0..7) {
                for (srcBits in 0..7) {
                    val opcode = 0x40 or (dstBits shl 3) or srcBits
                    if (opcode == 0x76) continue // HALT
                    if (dstBits == 6 || srcBits == 6) continue // (IX+d) — Phase 2.8
                    val dstHalf = halfFor(dstBits, high, low)
                    val srcHalf = halfFor(srcBits, high, low)
                    table[opcode] =
                        when {
                            dstHalf != null && srcHalf != null ->
                                LdIxHalfFromIxHalf(dstHalf, srcHalf)
                            dstHalf != null -> LdIxHalfFromReg(dstHalf, Reg.fromBits(srcBits))
                            srcHalf != null -> LdRegFromIxHalf(Reg.fromBits(dstBits), srcHalf)
                            else -> sharedNoHalf[dstBits][srcBits]
                        }
                }
            }
        }
    }

    private fun halfFor(bits: Int, high: IndexHalfReg, low: IndexHalfReg): IndexHalfReg? =
        when (bits) {
            4 -> high
            5 -> low
            else -> null
        }

    private fun installLdHalfImm(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            val high = if (idx == IndexReg.IX) IndexHalfReg.IXH else IndexHalfReg.IYH
            val low = if (idx == IndexReg.IX) IndexHalfReg.IXL else IndexHalfReg.IYL
            table[0x26] = LdIxHalfImm(high)
            table[0x2E] = LdIxHalfImm(low)
        }
    }

    private fun installIncDecHalves(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            val high = if (idx == IndexReg.IX) IndexHalfReg.IXH else IndexHalfReg.IYH
            val low = if (idx == IndexReg.IX) IndexHalfReg.IXL else IndexHalfReg.IYL
            table[0x24] = IncIxHalf(high)
            table[0x25] = DecIxHalf(high)
            table[0x2C] = IncIxHalf(low)
            table[0x2D] = DecIxHalf(low)
        }
    }

    private fun installAluAHalves(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            val high = if (idx == IndexReg.IX) IndexHalfReg.IXH else IndexHalfReg.IYH
            val low = if (idx == IndexReg.IX) IndexHalfReg.IXL else IndexHalfReg.IYL
            for (oooBits in 0..7) {
                val op = AluOp.fromBits(oooBits)
                // <ALU> A, IXH/IYH at 0x84 + (ooo << 3)
                table[0x84 or (oooBits shl 3)] = AluAFromIxHalf(op, high)
                // <ALU> A, IXL/IYL at 0x85 + (ooo << 3)
                table[0x85 or (oooBits shl 3)] = AluAFromIxHalf(op, low)
            }
        }
    }

    private fun installLdSpHlStraggler(d: Decoder) {
        d.main[0xF9] = LdSpHl
    }

    private fun installPairTouching(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            table[0x21] = LdIxImm(idx)
            table[0x22] = LdAddrFromIx(idx)
            table[0x23] = IncIx(idx)
            table[0x2A] = LdIxFromAddr(idx)
            table[0x2B] = DecIx(idx)
            table[0xE1] = PopIx(idx)
            table[0xE3] = ExSpIx(idx)
            table[0xE5] = PushIx(idx)
            table[0xF9] = LdSpFromIx(idx)
            // ADD IX/IY, rr — opcodes 09 (BC), 19 (DE), 29 (Self), 39 (SP)
            for (srcBits in 0..3) {
                val opcode = 0x09 or (srcBits shl 4)
                table[opcode] = AddIxPair(idx, srcBits)
            }
        }
    }

    private fun installIndexedLd(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            for (rrrBits in 0..7) {
                if (rrrBits == 6) continue
                table[0x46 or (rrrBits shl 3)] = LdRegFromIxd(idx, dst = Reg.fromBits(rrrBits))
                table[0x70 or rrrBits] = LdIxdFromReg(idx, src = Reg.fromBits(rrrBits))
            }
        }
    }

    private fun installIndexedLdImm(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            table[0x36] = LdIxdImm(idx)
        }
    }

    private fun installIndexedAlu(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            for (oooBits in 0..7) {
                val opcode = 0x86 or (oooBits shl 3)
                table[opcode] = AluAFromIxd(idx, op = AluOp.fromBits(oooBits))
            }
        }
    }

    private fun installIndexedIncDec(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            table[0x34] = IncIxd(idx)
            table[0x35] = DecIxd(idx)
        }
    }

    private fun installJpIx(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            table[0xE9] = JpIx(idx)
        }
    }
}
