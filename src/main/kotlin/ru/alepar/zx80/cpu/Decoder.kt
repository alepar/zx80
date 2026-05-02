package ru.alepar.zx80.cpu

import ru.alepar.zx80.op.Op

/**
 * Holds the seven 256-entry dispatch tables. Tables are populated once at startup by the
 * (yet-to-be-written) OpTableBuilder. Null entries mean "not yet implemented" and are counted by
 * the OpcodeCoverage harness suite.
 */
class Decoder {
    val main: Array<Op?> = arrayOfNulls(TABLE_SIZE)
    val cb: Array<Op?> = arrayOfNulls(TABLE_SIZE)
    val ed: Array<Op?> = arrayOfNulls(TABLE_SIZE)
    val dd: Array<Op?> = arrayOfNulls(TABLE_SIZE)
    val fd: Array<Op?> = arrayOfNulls(TABLE_SIZE)
    val ddcb: Array<Op?> = arrayOfNulls(TABLE_SIZE)
    val fdcb: Array<Op?> = arrayOfNulls(TABLE_SIZE)

    companion object {
        const val TABLE_SIZE = 256
    }
}
