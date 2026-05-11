package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import ru.alepar.zx80.machine.Beeper
import ru.alepar.zx80.machine.Keyboard
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.SpectrumIoBus
import ru.alepar.zx80.machine.UlaRenderer
import ru.alepar.zx80.ui.AudioOutput
import ru.alepar.zx80.ui.BeeperAudioSink
import ru.alepar.zx80.ui.NoOpAudioOutput
import ru.alepar.zx80.ui.Pacer
import ru.alepar.zx80.ui.SpectrumWindow

/** Launch the ZX Spectrum 48K emulator in a host window at integer scale. */
class SpectrumCommand : CliktCommand(name = "spectrum") {
    private val scale by option("--scale").int().default(2)
    private val noAudio by option("--no-audio", help = "Disable audio output").flag()

    override fun run() {
        val machine = Spectrum48k()
        val keyboard = Keyboard()
        val beeper = Beeper(machine.cpu)
        machine.cpu.io = SpectrumIoBus(keyboard, beeper)
        machine.reset()
        val audioOut = if (noAudio) NoOpAudioOutput else AudioOutput.tryOpen()
        val audioSink = BeeperAudioSink(beeper, audioOut)
        val pacer = Pacer(machine, UlaRenderer(), audioSink = audioSink)
        val window = SpectrumWindow(pacer, keyboard, scale)
        window.show()
    }
}
