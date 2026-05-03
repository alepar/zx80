package ru.alepar.zx80.op.stack

import ru.alepar.zx80.cpu.Decoder

/**
 * Registers the stack Op family (PUSH rr, POP rr) into the decoder. Called by
 * [ru.alepar.zx80.op.OpTableBuilder]. Filled in by WU 2.5-2.
 */
object StackOps {
    fun installInto(d: Decoder) {
        // Filled in by WU 2.5-2.
    }
}
