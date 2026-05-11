package ru.alepar.zx80.machine.tape

/**
 * Converts tape block data + timing parameters into a real-time EAR-pin waveform that the CPU can
 * sample via port 0xFE bit 6.
 *
 * Usage:
 * ```
 * val pulser = TapePulser(pilotPulse = 2168, ...)
 * pulser.start(blockData, currentTState)
 * // Later, inside SpectrumIoBus.read(0xFE):
 * val ear = pulser.earLevelAt(cpu.tStates)
 * ```
 *
 * Waveform structure (all in T-states):
 * 1. Pilot tone: [pilotToneLen] half-periods of [pilotPulse] each.
 * 2. Sync pulse 1: [sync1Pulse] T-states.
 * 3. Sync pulse 2: [sync2Pulse] T-states.
 * 4. Data: each bit is two equal half-periods — [zeroBitPulse] for 0, [oneBitPulse] for 1. Bits
 *    transmitted MSB-first. For the last byte only [lastByteBits] most-significant bits count.
 * 5. End pause: EAR stays low (0) for the remainder (pause duration).
 *
 * EAR polarity: starts HIGH (0x40) at [start], toggles at every edge. The return value of
 * [earLevelAt] is either 0x40 (bit-6 mask) or 0 — caller ORs it directly into the port-0xFE byte.
 */
class TapePulser(
    val pilotPulse: Int,
    val sync1Pulse: Int,
    val sync2Pulse: Int,
    val zeroBitPulse: Int,
    val oneBitPulse: Int,
    val pilotToneLen: Int,
    val lastByteBits: Int,
    val pauseMs: Int,
) {
    /** T-state at which [start] was called. -1 = not started. */
    private var startTState: Long = -1L

    /**
     * Sorted list of (absoluteTState, earLevelAfterEdge) pairs. Each edge flips the EAR level.
     * Populated by [start].
     */
    private val edges = mutableListOf<Long>()

    /** True once [start] has been called. */
    val isRunning: Boolean
        get() = startTState >= 0L

    /**
     * Load [data] and schedule all EAR edges starting at [startTState].
     *
     * @param data raw block bytes (flag + payload + parity as the tape file provides them).
     * @param startTState absolute T-state at which the first pilot edge fires.
     */
    fun start(data: ByteArray, startTState: Long) {
        this.startTState = startTState
        edges.clear()

        var t = startTState

        // ---- Pilot tone ----
        // [pilotToneLen] half-periods of [pilotPulse] T-states each.
        // We generate an edge at the start of each half-period.
        repeat(pilotToneLen) {
            edges.add(t)
            t += pilotPulse
        }

        // ---- Sync ----
        edges.add(t)
        t += sync1Pulse
        edges.add(t)
        t += sync2Pulse

        // ---- Data bits ----
        val byteCount = data.size
        for (byteIdx in data.indices) {
            val bitsInThisByte = if (byteIdx == byteCount - 1) lastByteBits else 8
            val b = data[byteIdx].toInt() and 0xFF
            // MSB-first: bit 7, 6, ... down to (8 - bitsInThisByte)
            for (bitPos in 7 downTo (8 - bitsInThisByte)) {
                val halfPeriod = if ((b shr bitPos) and 1 == 1) oneBitPulse else zeroBitPulse
                edges.add(t)
                t += halfPeriod
                edges.add(t)
                t += halfPeriod
            }
        }

        // Add a trailing edge to ensure EAR transitions to LOW right after the last data bit.
        // This marks the start of the end-pause period and ensures earLevelAt returns 0 after
        // all data has been transmitted.  We only need this if the current edge count is odd
        // (which means EAR would otherwise be HIGH after the last data edge).
        if (edges.size % 2 == 1) {
            // Odd edge count → currently HIGH after last data edge; add one more edge to go LOW.
            edges.add(t)
        }
        // Now edges.size is even → earLevelAt returns 0 after all edges.
    }

    /**
     * Returns the EAR level (0 or 0x40) that the CPU would sample at [tState].
     *
     * Before [startTState] → 0. After all edges → 0. Between edges, level = 0x40 if the count of
     * edges that have fired is odd (HIGH after odd number of transitions from the initial-LOW
     * state).
     */
    fun earLevelAt(tState: Long): Int {
        if (!isRunning || tState < startTState) return 0
        // Count edges that have fired (edge.tState <= tState).
        // edges is in ascending order; binary-search for the insertion point.
        var lo = 0
        var hi = edges.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (edges[mid] <= tState) lo = mid + 1 else hi = mid
        }
        // `lo` = number of edges fired.  Starts LOW; each edge flips.
        return if (lo % 2 == 1) 0x40 else 0
    }

    /** Reset to idle (no block loaded). */
    fun stop() {
        startTState = -1L
        edges.clear()
    }
}
