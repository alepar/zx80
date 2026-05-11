package ru.alepar.zx80.ui

import java.awt.event.KeyEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.machine.SpectrumKey

class HostKeyMapTest {
    @Test
    fun `VK_A maps to A`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_A)).containsExactly(SpectrumKey.A)
    }

    @Test
    fun `VK_ENTER maps to ENTER`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_ENTER)).containsExactly(SpectrumKey.ENTER)
    }

    @Test
    fun `VK_SHIFT maps to CAPS_SHIFT`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_SHIFT)).containsExactly(SpectrumKey.CAPS_SHIFT)
    }

    @Test
    fun `VK_CONTROL maps to SYMBOL_SHIFT`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_CONTROL)).containsExactly(SpectrumKey.SYMBOL_SHIFT)
    }

    @Test
    fun `VK_BACK_SPACE maps to CAPS_SHIFT + K0`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_BACK_SPACE))
            .containsExactly(SpectrumKey.CAPS_SHIFT, SpectrumKey.K0)
    }

    @Test
    fun `VK_LEFT maps to CAPS_SHIFT + K5`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_LEFT))
            .containsExactly(SpectrumKey.CAPS_SHIFT, SpectrumKey.K5)
    }

    @Test
    fun `VK_F12 is unmapped`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_F12)).isEmpty()
    }
}
