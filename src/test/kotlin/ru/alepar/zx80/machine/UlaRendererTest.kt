package ru.alepar.zx80.machine

import java.awt.image.BufferedImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Memory

class UlaRendererTest {

    private val renderer = UlaRenderer()

    /** BufferedImage.TYPE_INT_RGB returns getRGB() with alpha=0xFF; mask it for comparison. */
    private fun pixel(img: BufferedImage, x: Int, y: Int): Int = img.getRGB(x, y) and 0xFFFFFF

    @Test
    fun `all-zero screen renders all-black`() {
        val mem = Memory()
        val img = renderer.render(mem, flashOn = false)
        for (y in 0 until 192) {
            for (x in 0 until 256) {
                assertThat(pixel(img, x, y)).`as`("x=%d y=%d", x, y).isEqualTo(0x000000)
            }
        }
    }

    @Test
    fun `one cell top-left white ink black paper`() {
        val mem = Memory()
        mem.write(0x4000, 0xFF) // all 8 pixels on
        mem.write(0x5800, 0x07) // ink=white(7), paper=black(0)
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 0)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
        // Pixel just past the cell should still be black.
        assertThat(pixel(img, 8, 0)).isEqualTo(0x000000)
        assertThat(pixel(img, 0, 1)).isEqualTo(0x000000)
    }

    @Test
    fun `row-interleaving y=8 maps to address 0x4020`() {
        val mem = Memory()
        mem.write(0x4020, 0xFF) // pixel byte for screen row 8, x-byte 0
        mem.write(0x5800 + 32, 0x07) // attribute row 1 (y=8..15), col 0
        val img = renderer.render(mem, flashOn = false)
        // Row 8 cols 0..7 should be white.
        for (x in 0..7) {
            assertThat(pixel(img, x, 8)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
        // Rows 0..7 stay black.
        for (y in 0..7) {
            assertThat(pixel(img, 0, y)).`as`("y=%d", y).isEqualTo(0x000000)
        }
        // Row 9 stays black.
        assertThat(pixel(img, 0, 9)).isEqualTo(0x000000)
    }

    @Test
    fun `row-interleaving y=64 maps to address 0x4800 (second screen bank)`() {
        val mem = Memory()
        mem.write(0x4800, 0xFF) // pixel byte for screen row 64
        mem.write(0x5800 + 8 * 32, 0x07) // attribute row 8 (y=64..71)
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 64)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
        // Row 63 and 65 stay black.
        assertThat(pixel(img, 0, 63)).isEqualTo(0x000000)
        assertThat(pixel(img, 0, 65)).isEqualTo(0x000000)
    }

    @Test
    fun `row-interleaving y=128 maps to address 0x5000 (third screen bank)`() {
        val mem = Memory()
        mem.write(0x5000, 0xFF)
        mem.write(0x5800 + 16 * 32, 0x07) // attribute row 16 (y=128..135)
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 128)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
        assertThat(pixel(img, 0, 127)).isEqualTo(0x000000)
        assertThat(pixel(img, 0, 129)).isEqualTo(0x000000)
    }

    @Test
    fun `flash off, bit 7 of attr is ignored when flashOn=false`() {
        val mem = Memory()
        mem.write(0x4000, 0xFF)
        mem.write(0x5800, 0x87) // FLASH=1, ink=white, paper=black
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 0)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
    }

    @Test
    fun `flash on, bit 7 of attr inverts ink and paper when flashOn=true`() {
        val mem = Memory()
        mem.write(0x4000, 0xFF)
        mem.write(0x5800, 0x87) // FLASH=1, ink=white, paper=black
        val img = renderer.render(mem, flashOn = true)
        // Inverted: pixel bit=1 now selects paper (black).
        for (x in 0..7) {
            assertThat(pixel(img, x, 0)).`as`("x=%d", x).isEqualTo(0x000000)
        }
    }

    @Test
    fun `bright bit, attribute 0x47 renders 0xFFFFFF not 0xCDCDCD`() {
        val mem = Memory()
        mem.write(0x4000, 0xFF)
        mem.write(0x5800, 0x47) // BRIGHT=1, ink=white, paper=black
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 0)).`as`("x=%d", x).isEqualTo(0xFFFFFF)
        }
    }

    @Test
    fun `pixel bit zero selects paper not ink`() {
        val mem = Memory()
        mem.write(0x4000, 0x00) // all pixel bits off → all paper
        mem.write(0x5800, 0x38) // paper=white(7), ink=black(0), BRIGHT=0, FLASH=0
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 0)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
    }
}
