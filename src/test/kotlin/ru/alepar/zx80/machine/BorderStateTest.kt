package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BorderStateTest {

    @Test
    fun `fresh BorderState reads as 0`() {
        val border = BorderState()
        assertThat(border.read()).isEqualTo(0)
    }

    @Test
    fun `write(7) followed by read returns 7`() {
        val border = BorderState()
        border.write(7)
        assertThat(border.read()).isEqualTo(7)
    }

    @Test
    fun `write(0xFF) masks to low 3 bits and returns 7`() {
        val border = BorderState()
        border.write(0xFF)
        assertThat(border.read()).isEqualTo(7)
    }
}
