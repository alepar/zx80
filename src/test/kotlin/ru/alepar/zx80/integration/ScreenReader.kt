package ru.alepar.zx80.integration

import ru.alepar.zx80.cpu.Memory

/**
 * Reads the Spectrum's 256x192 screen as text by matching each 8x8 character cell against the
 * character ROM at 0x3D00. Each ASCII char (0x20..0x7F) has an 8-byte glyph at 0x3D00 +
 * 8*(charCode - 0x20).
 *
 * `null` is returned for cells that don't exactly match any glyph (graphic chars, user-defined
 * chars, blank-but-non-paper-styled cells). The grid is 32 columns x 24 rows.
 *
 * Uses the same row-interleave formula as UlaRenderer to find each pixel-row's screen-RAM address.
 */
class ScreenReader(private val mem: Memory) {

    /** Returns the matched ASCII char (0x20..0x7F) or null if no exact glyph match. */
    fun cellChar(row: Int, col: Int): Char? {
        require(row in 0 until 24) { "row $row out of range" }
        require(col in 0 until 32) { "col $col out of range" }
        val cellBytes = readCellBytes(row, col)
        for (charCode in 0x20..0x7F) {
            val romOffset = 0x3D00 + (charCode - 0x20) * 8
            var match = true
            for (i in 0..7) {
                if (mem.read(romOffset + i) != cellBytes[i]) {
                    match = false
                    break
                }
            }
            if (match) return charCode.toChar()
        }
        return null
    }

    /** 24 rows, 32 chars per row. Unmatched cells become '?'. */
    fun asText(): List<String> =
        (0 until 24).map { row ->
            (0 until 32).map { col -> cellChar(row, col) ?: '?' }.joinToString("")
        }

    /** True if any row contains [needle]. */
    fun contains(needle: String): Boolean = asText().any { it.contains(needle) }

    private fun readCellBytes(row: Int, col: Int): IntArray {
        val bytes = IntArray(8)
        for (i in 0..7) {
            val y = row * 8 + i
            // Row-interleave formula (matches UlaRenderer):
            val rowBase =
                0x4000 or ((y and 0xC0) shl 5) or ((y and 0x07) shl 8) or ((y and 0x38) shl 2)
            bytes[i] = mem.read(rowBase + col)
        }
        return bytes
    }
}
