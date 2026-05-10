package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.UlaRenderer
import ru.alepar.zx80.ui.Pacer
import ru.alepar.zx80.ui.SpectrumWindow

/** Launch the ZX Spectrum 48K emulator in a host window at integer scale. */
class SpectrumCommand : CliktCommand(name = "spectrum") {
    private val scale by option("--scale").int().default(2)

    override fun run() {
        val machine = Spectrum48k()
        machine.reset()
        val pacer = Pacer(machine, UlaRenderer())
        val window = SpectrumWindow(pacer, scale)
        window.show()
    }
}
