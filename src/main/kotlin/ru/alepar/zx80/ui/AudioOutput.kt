package ru.alepar.zx80.ui

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

/**
 * Sink for rendered PCM samples. Implementations: [JavaSoundAudioOutput] feeds a real device;
 * [NoOpAudioOutput] silently swallows. Use [tryOpen] to get the right one for the host.
 */
interface AudioOutput {
    fun push(buf: ByteArray, length: Int)

    fun close()

    companion object {
        private const val SAMPLE_RATE = 48_000f
        private const val SAMPLE_SIZE_BITS = 8
        private const val CHANNELS = 1
        private const val BUFFER_FRAMES = 4 // ~80ms of buffer (4 * 960 samples)

        /**
         * Try to open a 48 kHz 8-bit unsigned mono SourceDataLine. On any failure
         * (LineUnavailableException, device absent, format unsupported), log a warning to stderr
         * and return [NoOpAudioOutput] so emulation continues silently.
         */
        fun tryOpen(): AudioOutput {
            return try {
                val format =
                    AudioFormat(
                        AudioFormat.Encoding.PCM_UNSIGNED,
                        SAMPLE_RATE,
                        SAMPLE_SIZE_BITS,
                        CHANNELS,
                        1, // frame size in bytes (1 channel * 1 byte)
                        SAMPLE_RATE,
                        false, // little-endian (8-bit doesn't matter)
                    )
                val line = AudioSystem.getSourceDataLine(format)
                line.open(format, 960 * BUFFER_FRAMES)
                line.start()
                JavaSoundAudioOutput(line)
            } catch (e: LineUnavailableException) {
                System.err.println(
                    "WARN: audio device unavailable; running silently (${e.message})"
                )
                NoOpAudioOutput
            } catch (e: IllegalArgumentException) {
                System.err.println(
                    "WARN: audio format unsupported; running silently (${e.message})"
                )
                NoOpAudioOutput
            }
        }
    }
}

class JavaSoundAudioOutput(private val line: SourceDataLine) : AudioOutput {
    override fun push(buf: ByteArray, length: Int) {
        line.write(buf, 0, length)
    }

    override fun close() {
        line.drain()
        line.stop()
        line.close()
    }
}

object NoOpAudioOutput : AudioOutput {
    override fun push(buf: ByteArray, length: Int) {
        /* swallow */
    }

    override fun close() {}
}
