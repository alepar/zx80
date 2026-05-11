package ru.alepar.zx80.machine.tape

/**
 * Sealed hierarchy for TZX blocks. Only the block types in the M3 standard-block subset are decoded
 * into typed variants; unrecognised block IDs are wrapped in [TzxUnknown].
 *
 * Timing fields are in T-states unless noted. Pause fields are in milliseconds (per the TZX spec).
 */
sealed class TzxBlock

/**
 * Block ID 0x10 — Standard Speed Data.
 *
 * @param pauseMs pause after this block in milliseconds (default 1000).
 * @param data full block data (flag byte + payload + parity).
 */
data class TzxStandardData(val pauseMs: Int, val data: ByteArray) : TzxBlock() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TzxStandardData) return false
        return pauseMs == other.pauseMs && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * pauseMs + data.contentHashCode()
}

/**
 * Block ID 0x11 — Turbo Speed Data.
 *
 * All timing parameters are stored in the block header and used by the pulse loader.
 */
data class TzxTurboData(
    val pilotPulse: Int,
    val sync1Pulse: Int,
    val sync2Pulse: Int,
    val zeroBitPulse: Int,
    val oneBitPulse: Int,
    val pilotToneLen: Int,
    val lastByteBits: Int,
    val pauseMs: Int,
    val data: ByteArray,
) : TzxBlock() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TzxTurboData) return false
        return pilotPulse == other.pilotPulse &&
            sync1Pulse == other.sync1Pulse &&
            sync2Pulse == other.sync2Pulse &&
            zeroBitPulse == other.zeroBitPulse &&
            oneBitPulse == other.oneBitPulse &&
            pilotToneLen == other.pilotToneLen &&
            lastByteBits == other.lastByteBits &&
            pauseMs == other.pauseMs &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var r = pilotPulse
        r = 31 * r + sync1Pulse
        r = 31 * r + sync2Pulse
        r = 31 * r + zeroBitPulse
        r = 31 * r + oneBitPulse
        r = 31 * r + pilotToneLen
        r = 31 * r + lastByteBits
        r = 31 * r + pauseMs
        r = 31 * r + data.contentHashCode()
        return r
    }
}

/** Block ID 0x20 — Pause / Stop the Tape. [pauseMs] == 0 means "stop the tape". */
data class TzxPause(val pauseMs: Int) : TzxBlock()

/** Block ID 0x21 — Group Start. [name] is the group label string (ASCII). */
data class TzxGroupStart(val name: String) : TzxBlock()

/** Block ID 0x22 — Group End. No payload. */
object TzxGroupEnd : TzxBlock()

/** Block ID 0x30 — Text Description. [text] is a free-form ASCII string. */
data class TzxTextDescription(val text: String) : TzxBlock()

/**
 * Block ID 0x32 — Archive Info. [entries] is a list of (type, text) pairs where type is a
 * single-byte code from the TZX spec (0=full title, 1=publisher, etc.).
 */
data class TzxArchiveInfo(val entries: List<Pair<Int, String>>) : TzxBlock()

/**
 * Catch-all for any block ID not in the M3 subset. The raw bytes (excluding the block ID byte) are
 * preserved so the parser can skip over them correctly. [id] is the raw block-type byte.
 */
data class TzxUnknown(val id: Int, val raw: ByteArray) : TzxBlock() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TzxUnknown) return false
        return id == other.id && raw.contentEquals(other.raw)
    }

    override fun hashCode(): Int = 31 * id + raw.contentHashCode()
}
