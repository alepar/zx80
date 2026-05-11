package ru.alepar.zx80.machine.tape

/**
 * Holds the currently-loaded tape file and tracks playback position. Used by both the ROM-trap fast
 * path and the pulse-level slow path.
 *
 * The deck is always present on a [ru.alepar.zx80.machine.Spectrum48k]; when no tape is loaded its
 * methods are inert (no trap fires, ear-level stays at the idle constant).
 *
 * M3.3 additions: [pulser] is non-null when pulse mode is active. [startPulseBlock] launches the
 * pulse-level timeline for the current block; [earLevel] delegates to the pulser when active.
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

    /**
     * Active pulse-level player, or null when pulse mode is not engaged. Set by [startPulseBlock];
     * cleared when pulse mode is disengaged.
     */
    var pulser: TapePulser? = null
        private set

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

    /**
     * Start pulse-level playback of the current block at the given [startTState]. Creates a
     * [TapePulser] from the current block's timing parameters and data, then engages pulse mode.
     *
     * Skips informational blocks (same auto-skip logic as [currentTrapData]). Does nothing if no
     * tape is loaded or the tape has played out.
     */
    fun startPulseBlock(startTState: Long) {
        val t = tape ?: return
        // Skip informational blocks to find the next data block.
        while (blockIndex < blockCount()) {
            val block =
                when (t) {
                    is TapTapeFile -> t.blocks[blockIndex]
                    is TzxTapeFile -> t.blocks[blockIndex]
                }
            when (block) {
                is TapBlock -> {
                    pulser =
                        TapePulser(
                                pilotPulse = 2168,
                                sync1Pulse = 667,
                                sync2Pulse = 735,
                                zeroBitPulse = 855,
                                oneBitPulse = 1710,
                                pilotToneLen =
                                    if (
                                        block.data.isNotEmpty() &&
                                            (block.data[0].toInt() and 0xFF) == 0xFF
                                    )
                                        3223
                                    else 8063,
                                lastByteBits = 8,
                                pauseMs = 1000,
                            )
                            .also { it.start(block.data, startTState) }
                    return
                }
                is TzxStandardData -> {
                    pulser =
                        TapePulser(
                                pilotPulse = 2168,
                                sync1Pulse = 667,
                                sync2Pulse = 735,
                                zeroBitPulse = 855,
                                oneBitPulse = 1710,
                                pilotToneLen =
                                    if (
                                        block.data.isNotEmpty() &&
                                            (block.data[0].toInt() and 0xFF) == 0xFF
                                    )
                                        3223
                                    else 8063,
                                lastByteBits = 8,
                                pauseMs = block.pauseMs,
                            )
                            .also { it.start(block.data, startTState) }
                    return
                }
                is TzxTurboData -> {
                    pulser =
                        TapePulser(
                                pilotPulse = block.pilotPulse,
                                sync1Pulse = block.sync1Pulse,
                                sync2Pulse = block.sync2Pulse,
                                zeroBitPulse = block.zeroBitPulse,
                                oneBitPulse = block.oneBitPulse,
                                pilotToneLen = block.pilotToneLen,
                                lastByteBits = block.lastByteBits,
                                pauseMs = block.pauseMs,
                            )
                            .also { it.start(block.data, startTState) }
                    return
                }
                is TzxPause,
                is TzxGroupStart,
                TzxGroupEnd,
                is TzxTextDescription,
                is TzxArchiveInfo,
                is TzxUnknown -> blockIndex++ // skip informational blocks
            }
        }
    }

    /** Stop pulse-level playback and clear the pulser. */
    fun stopPulse() {
        pulser?.stop()
        pulser = null
    }

    /**
     * Returns the current EAR level for port-0xFE bit 6 (0x40 mask or 0). Delegates to the active
     * [pulser] if pulse mode is engaged; otherwise returns 0 (idle low). EAR idle is low on the
     * Spectrum 48K bus; bit 6 is only driven during tape playback.
     */
    fun earLevel(tState: Long): Int = pulser?.earLevelAt(tState) ?: 0
}
