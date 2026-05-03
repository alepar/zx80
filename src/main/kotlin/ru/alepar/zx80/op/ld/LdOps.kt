package ru.alepar.zx80.op.ld

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.cpu.RegPair

/**
 * Registers the LD Op family into the decoder. Called by [ru.alepar.zx80.op.OpTableBuilder].
 * Subsequent WUs extend this fragment with more LD variants (LD r,n; LD rr,nn; LD with memory
 * addresses).
 *
 * The 0x40-0x7F block (minus 0x76=HALT) is the LD r,r' table. Bits 3-5 encode dst; bits 0-2 encode
 * src. dst==6 means LD (HL),r; src==6 means LD r,(HL).
 */
object LdOps {
    fun installInto(d: Decoder) {
        installRegToReg(d)
        installImmediate(d)
        installPairImmediate(d)
    }

    private fun installPairImmediate(d: Decoder) {
        // LD rr, nn — opcode pattern: 00 pp 0001 where pp is RegPair bits.
        // Opcodes: 0x01 (BC), 0x11 (DE), 0x21 (HL), 0x31 (SP).
        for (pairBits in 0..3) {
            val opcode = 0x01 or (pairBits shl 4)
            d.main[opcode] = LdPairImm(pair = RegPair.fromBits(pairBits))
        }
    }

    private fun installImmediate(d: Decoder) {
        // LD r, n — opcode pattern: 00 rrr 110 where rrr is dst register bits.
        // Opcodes: 0x06 (B), 0x0E (C), 0x16 (D), 0x1E (E), 0x26 (H), 0x2E (L), 0x3E (A).
        for (dstBits in 0..7) {
            if (dstBits == 6) continue // 0x36 = LD (HL), n, registered separately
            val opcode = 0x06 or (dstBits shl 3)
            d.main[opcode] = LdRegImm(dst = Reg.fromBits(dstBits))
        }
        d.main[0x36] = LdHlMemImm
    }

    private fun installRegToReg(d: Decoder) {
        for (dstBits in 0..7) {
            for (srcBits in 0..7) {
                val opcode = 0x40 or (dstBits shl 3) or srcBits
                if (opcode == 0x76) continue
                d.main[opcode] =
                    when {
                        dstBits == 6 && srcBits == 6 ->
                            error("unreachable; opcode 0x76 was filtered")
                        dstBits == 6 -> LdHlFromReg(src = Reg.fromBits(srcBits))
                        srcBits == 6 -> LdRegFromHl(dst = Reg.fromBits(dstBits))
                        else -> LdRegReg(src = Reg.fromBits(srcBits), dst = Reg.fromBits(dstBits))
                    }
            }
        }
    }
}
