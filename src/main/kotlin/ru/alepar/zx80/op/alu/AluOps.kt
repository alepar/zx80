package ru.alepar.zx80.op.alu

import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Reg
import ru.alepar.zx80.cpu.RegPair

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
        installInc(d)
        installDec(d)
        installAddHl(d)
    }

    private fun installAddHl(d: Decoder) {
        // ADD HL,rr — 00 ss 1001 → 0x09, 0x19, 0x29, 0x39
        for (ssBits in 0..3) {
            val opcode = 0x09 or (ssBits shl 4)
            d.main[opcode] = AddHlPair(src = RegPair.fromBits(ssBits))
        }
    }

    private fun installInc(d: Decoder) {
        // INC r — pattern 00 rrr 100 → 0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C
        for (rrrBits in 0..7) {
            val opcode = 0x04 or (rrrBits shl 3)
            d.main[opcode] = if (rrrBits == 6) IncHlMem else IncReg(dst = Reg.fromBits(rrrBits))
        }
    }

    private fun installDec(d: Decoder) {
        // DEC r — pattern 00 rrr 101 → 0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x35, 0x3D
        for (rrrBits in 0..7) {
            val opcode = 0x05 or (rrrBits shl 3)
            d.main[opcode] = if (rrrBits == 6) DecHlMem else DecReg(dst = Reg.fromBits(rrrBits))
        }
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
