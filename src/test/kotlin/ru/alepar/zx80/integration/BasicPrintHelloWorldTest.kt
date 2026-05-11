package ru.alepar.zx80.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import ru.alepar.zx80.machine.Beeper
import ru.alepar.zx80.machine.BorderState
import ru.alepar.zx80.machine.Keyboard
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.SpectrumIoBus
import ru.alepar.zx80.machine.SpectrumKey

class BasicPrintHelloWorldTest {

    @Test
    @Tag("integration")
    fun `BASIC PRINT HELLO WORLD prints the string to the screen`() {
        val machine = Spectrum48k()
        val keyboard = Keyboard()
        machine.cpu.io = SpectrumIoBus(keyboard, Beeper(machine.cpu), BorderState())
        machine.reset()

        // Boot: ROM memory test (~82 frames) + initial © draw. 200 is comfortable headroom.
        repeat(200) { machine.runFrame() }

        val type = SpectrumTypist(machine, keyboard)
        type.press(SpectrumKey.P) // PRINT keyword
        type.withMod(SpectrumKey.SYMBOL_SHIFT, SpectrumKey.P) // "
        type.press(SpectrumKey.H)
        type.press(SpectrumKey.E)
        type.press(SpectrumKey.L)
        type.press(SpectrumKey.L)
        type.press(SpectrumKey.O)
        type.withMod(SpectrumKey.SYMBOL_SHIFT, SpectrumKey.N) // ,
        type.space()
        type.press(SpectrumKey.W)
        type.press(SpectrumKey.O)
        type.press(SpectrumKey.R)
        type.press(SpectrumKey.L)
        type.press(SpectrumKey.D)
        type.withMod(SpectrumKey.SYMBOL_SHIFT, SpectrumKey.P) // "
        type.enter()

        // BASIC parses + executes + reprints ready prompt.
        repeat(30) { machine.runFrame() }

        val reader = ScreenReader(machine.mem)
        val screenText = reader.asText().joinToString("\n")
        assertThat(reader.contains("hello, world"))
            .`as`("expected screen contents to contain hello, world; actual screen:\n$screenText")
            .isTrue
    }
}
