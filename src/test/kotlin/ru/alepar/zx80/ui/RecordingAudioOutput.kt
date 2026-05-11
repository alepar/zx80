package ru.alepar.zx80.ui

class RecordingAudioOutput : AudioOutput {
    val pushed: MutableList<ByteArray> = mutableListOf()

    override fun push(buf: ByteArray, length: Int) {
        pushed += buf.copyOf(length)
    }

    override fun close() {}
}
