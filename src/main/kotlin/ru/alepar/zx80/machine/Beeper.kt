package ru.alepar.zx80.machine

import ru.alepar.zx80.cpu.Cpu

/**
 * Captures OUT(0xFE) bit-4 toggles (the Spectrum's 1-bit beeper) and renders them to a 48 kHz
 * 8-bit unsigned PCM buffer once per frame.
 *
 * Single-threaded (Pacer thread only): SpectrumIoBus.write calls [onWrite] during runFrame;
 * Pacer's audio sink calls [beginFrame] before runFrame and [render] after.
 *
 * `events` holds T-state offsets (relative to frame start) at which the bit toggled. We don't
 * need to record the new value — toggles always flip; the starting [bit] is the post-render
 * carry-over.
 */
class Beeper(private val cpu: Cpu) {
    private val events = mutableListOf<Long>()
    private var bit: Int = 0
    private var frameStartTStates: Long = 0L

    /** Called from SpectrumIoBus.write when OUT(0xFE) bit 4 changes. */
    fun onWrite(newBit: Int) {
        val b = newBit and 1
        if (b != bit) {
            events.add(cpu.tStates - frameStartTStates)
            bit = b
        }
    }

    /** Called at start of each frame. Resets event log; preserves current [bit] for carry-over. */
    fun beginFrame() {
        frameStartTStates = cpu.tStates
        events.clear()
    }

    /**
     * Render 960 samples for the just-completed frame into [buf]. The starting bit is the value
     * of [bit] at frame begin (carried from the previous frame); each event flips it at the
     * sample whose T-state >= the event offset.
     */
    fun render(buf: ByteArray) {
        require(buf.size >= SAMPLES_PER_FRAME) { "buf must hold >= $SAMPLES_PER_FRAME samples" }
        // Sample n maps to T-state offset (n * T_STATES_PER_FRAME / SAMPLES_PER_FRAME).
        // Use floating-point division; inputs are integer; outputs are deterministic per input.
        var startBit = startBitForRender()
        var eventIdx = 0
        for (sample in 0 until SAMPLES_PER_FRAME) {
            val tStateAtSample = (sample.toDouble() * T_STATES_PER_FRAME / SAMPLES_PER_FRAME).toLong()
            while (eventIdx < events.size && events[eventIdx] <= tStateAtSample) {
                startBit = startBit xor 1
                eventIdx++
            }
            buf[sample] = if (startBit == 1) AMP_HI else AMP_LO
        }
    }

    /**
     * The bit at sample 0 of the current frame's render: it's the [bit] value AFTER all events,
     * carried over from the previous frame. We track this implicitly by NOT resetting [bit] in
     * [beginFrame] — the post-render value naturally carries.
     */
    private fun startBitForRender(): Int = bit xor (events.size and 1)

    companion object {
        const val SAMPLES_PER_FRAME = 960
        const val T_STATES_PER_FRAME = 69_888
        val AMP_HI: Byte = 0xA0.toByte()
        val AMP_LO: Byte = 0x60.toByte()
    }
}
