package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import java.nio.file.Files
import java.nio.file.Path
import ru.alepar.zx80.machine.Beeper
import ru.alepar.zx80.machine.BorderState
import ru.alepar.zx80.machine.BorderedUlaRenderer
import ru.alepar.zx80.machine.Keyboard
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.SpectrumIoBus
import ru.alepar.zx80.machine.UlaRenderer
import ru.alepar.zx80.machine.tape.TapParser
import ru.alepar.zx80.machine.tape.TzxParser
import ru.alepar.zx80.ui.AudioOutput
import ru.alepar.zx80.ui.BeeperAudioSink
import ru.alepar.zx80.ui.NoOpAudioOutput
import ru.alepar.zx80.ui.Pacer
import ru.alepar.zx80.ui.SpectrumWindow

/** Launch the ZX Spectrum 48K emulator in a host window at integer scale. */
class SpectrumCommand : CliktCommand(name = "spectrum") {
    private val scale by option("--scale").int().default(2)
    private val noAudio by option("--no-audio", help = "Disable audio output").flag()
    private val tapePath by option("--tape", help = "Path to a .tap or .tzx tape file to pre-load")
    private val noTapeTrap by
        option(
                "--no-tape-trap",
                help = "Force pulse-mode loading for all blocks (disable ROM-trap fast path)",
            )
            .flag()

    override fun run() {
        val machine = Spectrum48k()
        val keyboard = Keyboard()
        val beeper = Beeper(machine.cpu)
        val border = BorderState()
        machine.cpu.io =
            SpectrumIoBus(keyboard, beeper, border, machine.tapeDeck) { machine.cpu.tStates }

        // Load tape file if provided, before reset.
        tapePath?.let { path ->
            val file = Path.of(path)
            if (!Files.exists(file)) throw CliktError("Tape file not found: $path")
            val bytes = Files.readAllBytes(file)
            val name = file.fileName.toString().lowercase()
            val tapeFile =
                when {
                    name.endsWith(".tzx") -> TzxParser.parseTzx(bytes)
                    name.endsWith(".tap") -> TapParser.parseTap(bytes)
                    // Magic detection fallback: "ZXTape!" header = TZX
                    bytes.size >= 7 &&
                        bytes[0] == 0x5A.toByte() &&
                        bytes[1] == 0x58.toByte() &&
                        bytes[2] == 0x54.toByte() -> TzxParser.parseTzx(bytes)
                    else -> TapParser.parseTap(bytes)
                }
            machine.tapeDeck.loadTape(tapeFile)
        }

        if (noTapeTrap) machine.tapeDeck.trapEnabled = false

        machine.reset()
        val audioOut = if (noAudio) NoOpAudioOutput else AudioOutput.tryOpen()
        val audioSink = BeeperAudioSink(beeper, audioOut)
        val renderer = BorderedUlaRenderer(UlaRenderer(), border)
        val pacer = Pacer(machine, renderer, audioSink = audioSink)
        val window = SpectrumWindow(pacer, keyboard, scale)
        window.show()
    }
}
