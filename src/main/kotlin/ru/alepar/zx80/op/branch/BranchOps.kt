package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Condition
import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the branch Op family (JP, JR, DJNZ, CALL, RET, RST and their conditional variants) into
 * the decoder. Called by [ru.alepar.zx80.op.OpTableBuilder]. Subsequent WUs extend this fragment
 * with the actual installations.
 */
object BranchOps {
    fun installInto(d: Decoder) {
        installJpFamily(d)
    }

    private fun installJpFamily(d: Decoder) {
        d.main[0xC3] = JpAbs
        d.main[0xE9] = JpHl
        // JP cc, nn — pattern 11 ccc 010 → C2, CA, D2, DA, E2, EA, F2, FA
        for (cccBits in 0..7) {
            val opcode = 0xC2 or (cccBits shl 3)
            d.main[opcode] = JpAbsCc(cond = Condition.fromBits(cccBits))
        }
    }
}
