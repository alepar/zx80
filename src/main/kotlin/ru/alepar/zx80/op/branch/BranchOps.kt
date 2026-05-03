package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the branch Op family (JP, JR, DJNZ, CALL, RET, RST and their conditional variants) into
 * the decoder. Called by [ru.alepar.zx80.op.OpTableBuilder].
 */
object BranchOps {
    fun installInto(d: Decoder) {
        installJpFamily(d)
        installJrAndDjnz(d)
        installCallFamily(d)
        installRetFamily(d)
        installRstFamily(d)
    }

    private fun installJpFamily(d: Decoder) {
        d.main[0xC3] = JpAbs
        d.main[0xE9] = JpHl
        for (cccBits in 0..7) {
            val opcode = 0xC2 or (cccBits shl 3)
            d.main[opcode] = JpAbsCc(cond = Condition.fromBits(cccBits))
        }
    }

    private fun installJrAndDjnz(d: Decoder) {
        d.main[0x18] = JrRel
        d.main[0x10] = Djnz
        d.main[0x20] = JrRelCc(cond = Condition.NZ)
        d.main[0x28] = JrRelCc(cond = Condition.Z)
        d.main[0x30] = JrRelCc(cond = Condition.NC)
        d.main[0x38] = JrRelCc(cond = Condition.C)
    }

    private fun installCallFamily(d: Decoder) {
        d.main[0xCD] = CallAbs
        for (cccBits in 0..7) {
            val opcode = 0xC4 or (cccBits shl 3)
            d.main[opcode] = CallAbsCc(cond = Condition.fromBits(cccBits))
        }
    }

    private fun installRetFamily(d: Decoder) {
        d.main[0xC9] = Ret
        // RET cc — pattern 11 ccc 000 → C0, C8, D0, D8, E0, E8, F0, F8
        for (cccBits in 0..7) {
            val opcode = 0xC0 or (cccBits shl 3)
            d.main[opcode] = RetCc(cond = Condition.fromBits(cccBits))
        }
    }

    private fun installRstFamily(d: Decoder) {
        // RST p — pattern 11 ttt 111 → C7, CF, D7, DF, E7, EF, F7, FF
        for (tttBits in 0..7) {
            val opcode = 0xC7 or (tttBits shl 3)
            d.main[opcode] = Rst(target = tttBits * 8)
        }
    }
}
