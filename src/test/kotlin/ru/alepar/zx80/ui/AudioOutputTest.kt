package ru.alepar.zx80.ui

import org.junit.jupiter.api.Test

class AudioOutputTest {

    @Test
    fun `NoOpAudioOutput push does not throw`() {
        NoOpAudioOutput.push(ByteArray(960), 960)
    }

    @Test
    fun `NoOpAudioOutput close does not throw`() {
        NoOpAudioOutput.close()
    }
}
