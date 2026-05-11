package ru.alepar.zx80.machine.tape

/**
 * One block from a .tap file. The [data] array contains all bytes of the block as stored in the
 * file (flag byte first, payload bytes, parity byte last). The length field from the file is
 * implicit in [data].size.
 */
data class TapBlock(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TapBlock) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}
