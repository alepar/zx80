package ru.alepar.zx80.machine

import java.awt.image.BufferedImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Memory

class BorderedUlaRendererTest {

    private fun pixel(img: BufferedImage, x: Int, y: Int): Int = img.getRGB(x, y) and 0xFFFFFF

    @Test
    fun `output dimensions are 352 by 288`() {
        val border = BorderState()
        val r = BorderedUlaRenderer(UlaRenderer(), border)
        val img = r.render(Memory(), flashOn = false)
        assertThat(img.width).isEqualTo(352)
        assertThat(img.height).isEqualTo(288)
    }

    @Test
    fun `border pixels match BorderState color`() {
        val border = BorderState().apply { write(2) } // red
        val r = BorderedUlaRenderer(UlaRenderer(), border)
        val img = r.render(Memory(), flashOn = false)
        assertThat(pixel(img, 0, 0)).isEqualTo(0xCD0000) // red non-bright
        assertThat(pixel(img, 351, 287)).isEqualTo(0xCD0000)
    }

    @Test
    fun `screen pixels at offset 48,48 match inner UlaRenderer output`() {
        val mem = Memory()
        mem.write(0x4000, 0xFF) // top-left cell pixel row, all pixels on
        mem.write(0x5800, 0x07) // ink=white, paper=black
        val r = BorderedUlaRenderer(UlaRenderer(), BorderState())
        val img = r.render(mem, flashOn = false)
        // (48, 48) is the top-left of the screen area; white because ink + bit=1.
        assertThat(pixel(img, 48, 48)).isEqualTo(0xCDCDCD)
        // (47, 48) is just inside the left border; black (border color 0).
        assertThat(pixel(img, 47, 48)).isEqualTo(0x000000)
    }

    @Test
    fun `changing BorderState between renders changes border color`() {
        val border = BorderState()
        val r = BorderedUlaRenderer(UlaRenderer(), border)
        border.write(0)
        val img0 = r.render(Memory(), flashOn = false)
        border.write(5) // cyan
        val img5 = r.render(Memory(), flashOn = false)
        assertThat(pixel(img0, 0, 0)).isEqualTo(0x000000)
        assertThat(pixel(img5, 0, 0)).isEqualTo(0x00CDCD)
    }

    @Test
    fun `bottom and right border pixels are border color`() {
        val border = BorderState().apply { write(4) } // green
        val r = BorderedUlaRenderer(UlaRenderer(), border)
        val img = r.render(Memory(), flashOn = false)
        // (256+48, anywhere in screen Y range) is in the right border.
        assertThat(pixel(img, 304, 100)).isEqualTo(0x00CD00)
        // (anywhere in screen X, 192+48) is in the bottom border.
        assertThat(pixel(img, 100, 240)).isEqualTo(0x00CD00)
    }
}
