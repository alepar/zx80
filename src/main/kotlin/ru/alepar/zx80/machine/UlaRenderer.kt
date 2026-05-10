package ru.alepar.zx80.machine

import java.awt.image.BufferedImage
import ru.alepar.zx80.cpu.Memory

/**
 * Reads ZX Spectrum 48K screen RAM (0x4000-0x5AFF) and produces a 256x192 BufferedImage.
 * Headless; no host window. M2.4 wraps the output in a display; M2.8 will add a border.
 *
 * `flashOn` selects the flash phase: false = ink and paper as written; true = ink and paper
 * swapped for cells with attribute bit 7 set. The caller (M2.4) toggles this every 16 frames.
 *
 * Pixel-data layout (0x4000-0x57FF, 6144 bytes): rows are interleaved into three banks of 64
 * rows each. Address for screen row y:
 *
 *     0x4000 | ((y and 0xC0) shl 5) | ((y and 0x07) shl 8) | ((y and 0x38) shl 2)
 *
 * Attributes (0x5800-0x5AFF, 768 bytes): one byte per 8x8 cell, laid out linearly.
 * Byte format:
 *   bit 7    : FLASH
 *   bit 6    : BRIGHT
 *   bits 3-5 : PAPER color (0..7)
 *   bits 0-2 : INK color (0..7)
 *
 * A pixel bit of 1 selects INK; 0 selects PAPER.
 */
class UlaRenderer {

    fun render(mem: Memory, flashOn: Boolean = false): BufferedImage {
        val img = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until HEIGHT) {
            val rowBase =
                0x4000 or
                    ((y and 0xC0) shl 5) or
                    ((y and 0x07) shl 8) or
                    ((y and 0x38) shl 2)
            val attrBase = 0x5800 + (y ushr 3) * 32
            for (xByte in 0 until 32) {
                val pixels = mem.read(rowBase + xByte)
                val attr = mem.read(attrBase + xByte)
                val bright = (attr and 0x40) != 0
                val ink = SpectrumPalette.color(attr and 0x07, bright)
                val paper = SpectrumPalette.color((attr ushr 3) and 0x07, bright)
                val flash = (attr and 0x80) != 0
                val fg = if (flash && flashOn) paper else ink
                val bg = if (flash && flashOn) ink else paper
                val pxBase = xByte * 8
                for (bit in 0..7) {
                    val on = (pixels shr (7 - bit)) and 1
                    img.setRGB(pxBase + bit, y, if (on == 1) fg else bg)
                }
            }
        }
        return img
    }

    companion object {
        const val WIDTH = 256
        const val HEIGHT = 192
    }
}
