package ru.alepar.zx80.harness.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.harness.fuse.PortRead

class QueueIoBusTest {
    @Test
    fun `read returns bytes in order then 0xFF when queue is empty`() {
        val bus = QueueIoBus(listOf(PortRead(0x100, 0x55), PortRead(0x101, 0xAA)))
        assertThat(bus.read(0x100)).isEqualTo(0x55)
        assertThat(bus.read(0x101)).isEqualTo(0xAA)
        // Queue exhausted — fallback to 0xFF (matches the default IoBus behavior)
        assertThat(bus.read(0x999)).isEqualTo(0xFF)
    }

    @Test
    fun `write is a no-op (FUSE doesn't validate ports here)`() {
        val bus = QueueIoBus(emptyList())
        bus.write(0x100, 0x42) // should not throw
    }

    @Test
    fun `read ignores port number and pops in order regardless of port`() {
        // FUSE's PR events list port for documentation; the byte is what matters.
        val bus = QueueIoBus(listOf(PortRead(0x100, 0x11), PortRead(0x200, 0x22)))
        // Read with a totally different port — still returns 0x11 (the queue head)
        assertThat(bus.read(0xFFFF)).isEqualTo(0x11)
        assertThat(bus.read(0x0000)).isEqualTo(0x22)
    }
}
