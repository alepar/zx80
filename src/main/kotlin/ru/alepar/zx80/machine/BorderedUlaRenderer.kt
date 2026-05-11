package ru.alepar.zx80.machine

import java.awt.Color
import java.awt.image.BufferedImage
import ru.alepar.zx80.cpu.Memory

/**
 * Wraps [UlaRenderer] and adds a 48-pixel Spectrum border around the 256x192 screen, producing a
 * 352x288 framebuffer. The border color comes from [BorderState] (read once per render call; no
 * mid-frame changes).
 */
class BorderedUlaRenderer(private val inner: UlaRenderer, private val border: BorderState) {
    fun render(mem: Memory, flashOn: Boolean = false): BufferedImage {
        val screen = inner.render(mem, flashOn)
        val out = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.color = Color(SpectrumPalette.color(border.read(), bright = false))
            g.fillRect(0, 0, WIDTH, HEIGHT)
            g.drawImage(screen, BORDER, BORDER, null)
        } finally {
            g.dispose()
        }
        return out
    }

    companion object {
        const val BORDER = 48
        const val WIDTH = 256 + BORDER * 2 // 352
        const val HEIGHT = 192 + BORDER * 2 // 288
    }
}
