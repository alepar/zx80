package ru.alepar.zx80.machine.tape

/**
 * Holds the currently-loaded tape file and tracks playback position. Used by both the ROM-trap fast
 * path and the pulse-level slow path.
 *
 * The deck is always present on a [ru.alepar.zx80.machine.Spectrum48k]; when no tape is loaded its
 * methods are inert (no trap fires, ear-level stays at the idle constant).
 *
 * M3.2 scope: ROM-trap path only. Pulse-level fields land in M3.3.
 */
class TapeDeck {
    /** Currently-loaded tape file, or null if no tape is mounted. */
    private var tape: TapeFile? = null

    /** Index of the next block the loader will read. Advances after each successful load. */
    private var blockIndex: Int = 0

    /**
     * When false, the ROM-trap path is bypassed and the CPU runs the real Sinclair loader against
     * pulse-mode data. Defaults to true (fast path on).
     */
    var trapEnabled: Boolean = true

    /** Mount [file] and reset the block index. Pass null to eject the tape. */
    fun loadTape(file: TapeFile?) {
        tape = file
        blockIndex = 0
    }

    /** True iff a tape is mounted. */
    fun hasTape(): Boolean = tape != null

    /** Total number of blocks on the mounted tape (0 if no tape). */
    fun blockCount(): Int =
        when (val t = tape) {
            null -> 0
            is TapTapeFile -> t.blocks.size
            is TzxTapeFile -> t.blocks.size
        }

    /** Current block index (0-based). Equals [blockCount] when the tape has played out. */
    fun currentBlockIndex(): Int = blockIndex

    /**
     * Returns the raw data bytes of the next block IF it's "trappable" by the ROM-trap loader (i.e.
     * a standard-speed data block whose payload is a flag byte + payload + parity byte). Returns
     * null if no tape is loaded, the tape has played out, the trap is disabled, or the current
     * block is not a trappable type.
     *
     * Non-data blocks ([TzxPause], [TzxGroupStart], [TzxTextDescription], [TzxArchiveInfo],
     * [TzxGroupEnd], [TzxUnknown]) are auto-skipped — the deck advances past them transparently and
     * returns the next data block (if any). [TzxTurboData] is NOT trappable; the trap must skip the
     * call and let pulse mode handle it.
     */
    fun currentTrapData(): ByteArray? {
        if (!trapEnabled) return null
        val t = tape ?: return null
        // Skip any non-data informational blocks (TZX-only).
        while (blockIndex < blockCount()) {
            val block =
                when (t) {
                    is TapTapeFile -> t.blocks[blockIndex]
                    is TzxTapeFile -> t.blocks[blockIndex]
                }
            when (block) {
                is TapBlock -> return block.data
                is TzxStandardData -> return block.data
                is TzxTurboData -> return null // not trappable; defer to pulse mode
                is TzxPause,
                is TzxGroupStart,
                TzxGroupEnd,
                is TzxTextDescription,
                is TzxArchiveInfo,
                is TzxUnknown -> blockIndex++ // skip and continue scanning
            }
        }
        return null
    }

    /** Advance past the current block. Called by the trap after successfully consuming the data. */
    fun advanceBlock() {
        if (blockIndex < blockCount()) blockIndex++
    }
}
