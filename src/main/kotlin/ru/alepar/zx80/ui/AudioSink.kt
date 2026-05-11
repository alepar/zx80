package ru.alepar.zx80.ui

import ru.alepar.zx80.machine.Beeper

/**
 * Per-frame hook called by [Pacer] around runFrame. Tests can use [NoOpAudioSink]; production uses
 * [BeeperAudioSink].
 */
interface AudioSink {
    fun beforeFrame()

    fun afterFrame()
}

object NoOpAudioSink : AudioSink {
    override fun beforeFrame() {}

    override fun afterFrame() {}
}

class BeeperAudioSink(private val beeper: Beeper, private val out: AudioOutput) : AudioSink {
    private val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)

    override fun beforeFrame() {
        beeper.beginFrame()
    }

    override fun afterFrame() {
        beeper.render(buf)
        out.push(buf, buf.size)
    }
}
