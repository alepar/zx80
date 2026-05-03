package ru.alepar.zx80.op.branch

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the branch Op family (JP, JR, DJNZ, CALL, RET, RST and their conditional variants) into
 * the decoder. Called by [ru.alepar.zx80.op.OpTableBuilder]. Subsequent WUs extend this fragment
 * with the actual installations.
 */
object BranchOps {
    fun installInto(d: Decoder) {
        // Filled in by WUs 2.4-2 through 2.4-5.
    }
}
