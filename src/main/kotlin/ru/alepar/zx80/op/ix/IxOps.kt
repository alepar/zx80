package ru.alepar.zx80.op.ix

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.IndexReg
import ru.alepar.zx80.cpu.Reg

/**
 * Registers the DD/FD-prefixed (IX/IY) Op family into decoder.dd and decoder.fd. Also registers the
 * LD SP,HL straggler at decoder.main[0xF9] (it sits naturally alongside LD SP,IX and LD SP,IY).
 */
object IxOps {
    fun installInto(d: Decoder) {
        installLdSpHlStraggler(d)
        installPairTouching(d)
        installIndexedLd(d)
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
}
