package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import ru.alepar.zx80.machine.Keyboard
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.SpectrumIoBus
import ru.alepar.zx80.machine.UlaRenderer
import ru.alepar.zx80.ui.Pacer
import ru.alepar.zx80.ui.SpectrumWindow

/** Launch the ZX Spectrum 48K emulator in a host window at integer scale. */
class SpectrumCommand : CliktCommand(name = "spectrum") {
    private val scale by option("--scale").int().default(2)

    override fun run() {
        val machine = Spectrum48k()
        val keyboard = Keyboard()
        machine.cpu.io = SpectrumIoBus(keyboard)
        machine.reset()
        val pacer = Pacer(machine, UlaRenderer())
        val window = SpectrumWindow(pacer, keyboard, scale)
        window.show()
    }
}
