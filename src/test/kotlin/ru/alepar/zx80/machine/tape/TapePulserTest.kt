package ru.alepar.zx80.machine.tape

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [TapePulser] — converts tape block data + timing parameters into a real-time
 * EAR-pin waveform indexed by T-state timestamp.
 *
 * Standard timing constants (all T-states unless noted): pilot half-period: 2168 sync1: 667 sync2:
 * 735 zero bit half-period: 855 one bit half-period: 1710 pilot pulses: 8063
 */
class TapePulserTest {

    // Helper to build a TapePulser with standard turbo-block timing.
    private fun standardPulser(
        data: ByteArray,
        startTState: Long = 0L,
        pilotPulse: Int = 2168,
        sync1Pulse: Int = 667,
        sync2Pulse: Int = 735,
        zeroBitPulse: Int = 855,
        oneBitPulse: Int = 1710,
        pilotToneLen: Int = 8063,
        lastByteBits: Int = 8,
        pauseMs: Int = 1000,
    ): TapePulser {
        val pulser =
            TapePulser(
                pilotPulse = pilotPulse,
                sync1Pulse = sync1Pulse,
                sync2Pulse = sync2Pulse,
                zeroBitPulse = zeroBitPulse,
                oneBitPulse = oneBitPulse,
                pilotToneLen = pilotToneLen,
                lastByteBits = lastByteBits,
                pauseMs = pauseMs,
            )
        pulser.start(data, startTState)
        return pulser
    }

    @Test
    fun `ear level is low before start tstate`() {
        val pulser = standardPulser(byteArrayOf(0x00), startTState = 100L)
        // Before start: EAR is low (0)
        assertThat(pulser.earLevelAt(50L)).isEqualTo(0)
        assertThat(pulser.earLevelAt(99L)).isEqualTo(0)
    }

    @Test
    fun `pilot tone starts high at start tstate`() {
        val pulser = standardPulser(byteArrayOf(0x00), startTState = 0L)
        // First half of first pilot pulse: EAR high (0x40)
        assertThat(pulser.earLevelAt(0L)).isEqualTo(0x40)
    }

    @Test
    fun `pilot tone alternates at half-period boundaries`() {
        val pilotHalf = 2168L
        val pulser = standardPulser(byteArrayOf(0x00), startTState = 0L)
        // Half 0: 0..2167 → high
        assertThat(pulser.earLevelAt(0L)).isEqualTo(0x40)
        assertThat(pulser.earLevelAt(pilotHalf - 1)).isEqualTo(0x40)
        // Half 1: 2168..4335 → low
        assertThat(pulser.earLevelAt(pilotHalf)).isEqualTo(0)
        assertThat(pulser.earLevelAt(pilotHalf * 2 - 1)).isEqualTo(0)
        // Half 2: 4336..6503 → high
        assertThat(pulser.earLevelAt(pilotHalf * 2)).isEqualTo(0x40)
    }

    @Test
    fun `sync pulses follow pilot tone`() {
        // After 8063 pilot half-periods × 2168 T-states each, we get sync1 then sync2.
        val pilotEnd = 8063L * 2168L
        val pulser = standardPulser(byteArrayOf(0x00), startTState = 0L)
        // sync1 comes right after pilot end (667 T-states of the opposite polarity to the last
        // pilot)
        // The last pilot half (index 8062) is: 8062 mod 2 = 0 → high. So sync1 should be low.
        assertThat(pulser.earLevelAt(pilotEnd)).isEqualTo(0) // sync1 low
        assertThat(pulser.earLevelAt(pilotEnd + 666)).isEqualTo(0) // still sync1
        // sync2: next 735 T-states, opposite polarity → high
        assertThat(pulser.earLevelAt(pilotEnd + 667)).isEqualTo(0x40) // sync2 high
        assertThat(pulser.earLevelAt(pilotEnd + 667 + 734)).isEqualTo(0x40) // still sync2
    }

    @Test
    fun `data bit 0 has half-period of 855 tStates`() {
        // After pilot + sync, the first data bit from the byte 0x00 is a 0-bit.
        val dataStart = 8063L * 2168L + 667L + 735L
        val pulser = standardPulser(byteArrayOf(0x00), startTState = 0L)
        // After sync2 (which is high), the first half of bit-0 is low (opposite)
        assertThat(pulser.earLevelAt(dataStart)).isEqualTo(0) // first half low
        assertThat(pulser.earLevelAt(dataStart + 854)).isEqualTo(0) // still first half
        assertThat(pulser.earLevelAt(dataStart + 855)).isEqualTo(0x40) // second half high
        assertThat(pulser.earLevelAt(dataStart + 855 + 854)).isEqualTo(0x40)
        // Next bit also a 0 → low again
        assertThat(pulser.earLevelAt(dataStart + 855 * 2)).isEqualTo(0)
    }

    @Test
    fun `data bit 1 has half-period of 1710 tStates`() {
        // Byte 0xFF has all 1-bits. After pilot+sync, check half-period is 1710.
        val dataStart = 8063L * 2168L + 667L + 735L
        val pulser = standardPulser(byteArrayOf(0xFF.toByte()), startTState = 0L)
        // After sync2 (high), first half of bit-1 is low
        assertThat(pulser.earLevelAt(dataStart)).isEqualTo(0) // first half low
        assertThat(pulser.earLevelAt(dataStart + 1709)).isEqualTo(0) // still first half
        assertThat(pulser.earLevelAt(dataStart + 1710)).isEqualTo(0x40) // second half high
        assertThat(pulser.earLevelAt(dataStart + 1710 + 1709)).isEqualTo(0x40)
        // Second bit also 1 → low again
        assertThat(pulser.earLevelAt(dataStart + 1710 * 2)).isEqualTo(0)
    }

    @Test
    fun `ear is low after end pause`() {
        // A single byte, pause 0ms → EAR goes low immediately after data
        val data = byteArrayOf(0xFF.toByte())
        val pilotEnd = 8063L * 2168L
        val syncLen = 667L + 735L
        val dataLen = 8L * 1710L * 2L // 8 one-bits × 2 halves × 1710 each
        val totalContent = pilotEnd + syncLen + dataLen
        val pulser = standardPulser(data, startTState = 0L, pauseMs = 0)
        // After all content: EAR should be low
        assertThat(pulser.earLevelAt(totalContent + 1)).isEqualTo(0)
    }

    @Test
    fun `start tstate offset shifts entire timeline`() {
        val offset = 50_000L
        val pilotHalf = 2168L
        val pulser = standardPulser(byteArrayOf(0x00), startTState = offset)
        // Before offset: low
        assertThat(pulser.earLevelAt(offset - 1)).isEqualTo(0)
        // At offset: first pilot half (high)
        assertThat(pulser.earLevelAt(offset)).isEqualTo(0x40)
        // At offset + pilotHalf: second half (low)
        assertThat(pulser.earLevelAt(offset + pilotHalf)).isEqualTo(0)
    }

    @Test
    fun `bits are transmitted MSB first`() {
        // Byte 0x80 = 1000_0000. MSB is 1, rest are 0.
        // First bit after sync should be a 1-bit (half-period 1710), second a 0-bit (855).
        val dataStart = 8063L * 2168L + 667L + 735L
        val pulser = standardPulser(byteArrayOf(0x80.toByte()), startTState = 0L)
        // First half of bit 7 (MSB=1): low, period 1710
        assertThat(pulser.earLevelAt(dataStart + 1709)).isEqualTo(0) // still first half of 1-bit
        assertThat(pulser.earLevelAt(dataStart + 1710)).isEqualTo(0x40) // second half of 1-bit
        // After first bit (1): two halves at 1710 each = 3420 total
        // Second bit (bit 6 = 0): first half low at 855
        assertThat(pulser.earLevelAt(dataStart + 1710 * 2)).isEqualTo(0) // first half of 0-bit
        assertThat(pulser.earLevelAt(dataStart + 1710 * 2 + 854)).isEqualTo(0)
        assertThat(pulser.earLevelAt(dataStart + 1710 * 2 + 855))
            .isEqualTo(0x40) // second half of 0-bit
    }

    @Test
    fun `lastByteBits limits significant bits of final byte`() {
        // With lastByteBits=4 and one byte 0xF0, only the top 4 bits are transmitted.
        // 0xF0 = 1111_0000; top 4 bits are 1,1,1,1.
        val dataStart = 8063L * 2168L + 667L + 735L
        val pulser =
            standardPulser(
                byteArrayOf(0xF0.toByte()),
                startTState = 0L,
                lastByteBits = 4,
                pauseMs = 0,
            )
        // First 4 bits should be 1-bits (period 1710 each).
        // After those 4 bits (4 × 2 × 1710 = 13680 T-states), the stream ends.
        val after4Bits = dataStart + 4L * 2L * 1710L
        // No more edges after this → EAR stays low
        assertThat(pulser.earLevelAt(after4Bits)).isEqualTo(0)
    }
}
