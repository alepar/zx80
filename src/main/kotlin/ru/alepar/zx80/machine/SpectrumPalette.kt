package ru.alepar.zx80.machine

/**
 * Canonical ZX Spectrum 48K palette: 8 colors × 2 brightness levels.
 *
 * Per Sean Young's TUZD and Wikipedia "ZX Spectrum graphic modes": non-bright values use 0xCD
 * on active channels; bright values use 0xFF. Black is 0x000000 in both phases.
 *
 * Returned as packed RGB ints (alpha bits = 0) suitable for `BufferedImage.setRGB` with
 * TYPE_INT_RGB images.
 *
 * Spectrum color encoding: bit 0 = blue, bit 1 = red, bit 2 = green (so 7 = white).
 */
object SpectrumPalette {

    private const val L = 0xCD // non-bright "low" intensity
    private const val H = 0xFF // bright "high" intensity

    private val table =
        intArrayOf(
            rgb(0, 0, 0), // 0  black
            rgb(0, 0, L), // 1  blue
            rgb(L, 0, 0), // 2  red
            rgb(L, 0, L), // 3  magenta
            rgb(0, L, 0), // 4  green
            rgb(0, L, L), // 5  cyan
            rgb(L, L, 0), // 6  yellow
            rgb(L, L, L), // 7  white (non-bright)
            rgb(0, 0, 0), // 8  black bright (still black)
            rgb(0, 0, H), // 9  blue bright
            rgb(H, 0, 0), // 10 red bright
            rgb(H, 0, H), // 11 magenta bright
            rgb(0, H, 0), // 12 green bright
            rgb(0, H, H), // 13 cyan bright
            rgb(H, H, 0), // 14 yellow bright
            rgb(H, H, H), // 15 white bright
        )

    /** Look up a packed RGB color for a Spectrum 3-bit color index. */
    fun color(index: Int, bright: Boolean): Int {
        val i = (index and 0x07) or (if (bright) 0x08 else 0)
        return table[i]
    }

    private fun rgb(r: Int, g: Int, b: Int): Int =
        ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
