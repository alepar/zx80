package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Reg

/**
 * Registers the 8-bit ALU + INC/DEC Op family into the decoder. Called by
 * [ru.alepar.zx80.op.OpTableBuilder]. Subsequent WUs extend this fragment with ALU A,(HL); ALU A,n;
 * INC; DEC.
 *
 * The 0x80-0xBF block is the ALU A,r table. Bits 5-3 encode the ALU op (ooo); bits 2-0 encode the
 * source register (rrr). rrr=110 means source is (HL) — handled by a separate Op class registered
 * later.
 */
object AluOps {
    fun installInto(d: Decoder) {
        installAluAReg(d)
        installAluAFromHl(d)
        installAluAImm(d)
    }

    private fun installAluAImm(d: Decoder) {
        // ALU A,n — opcode pattern 11 ooo 110 → 0xC6, 0xCE, 0xD6, 0xDE, 0xE6, 0xEE, 0xF6, 0xFE
        for (oooBits in 0..7) {
            val opcode = 0xC0 or (oooBits shl 3) or 0x06
            d.main[opcode] = AluAImm(op = AluOp.fromBits(oooBits))
        }
    }

    private fun installAluAFromHl(d: Decoder) {
        // ALU A,(HL) — opcode pattern 10 ooo 110 → 0x86, 0x8E, 0x96, 0x9E, 0xA6, 0xAE, 0xB6, 0xBE
        for (oooBits in 0..7) {
            val opcode = 0x80 or (oooBits shl 3) or 0x06
            d.main[opcode] = AluAFromHl(op = AluOp.fromBits(oooBits))
        }
    }

    private fun installAluAReg(d: Decoder) {
        for (oooBits in 0..7) {
            val op = AluOp.fromBits(oooBits)
            for (rrrBits in 0..7) {
                if (rrrBits == 6) continue
                val opcode = 0x80 or (oooBits shl 3) or rrrBits
                d.main[opcode] = AluAReg(op = op, src = Reg.fromBits(rrrBits))
            }
        }
    }
}
