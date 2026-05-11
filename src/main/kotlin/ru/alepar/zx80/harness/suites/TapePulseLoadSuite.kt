package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.alepar.zx80.harness.SuiteResult
import ru.alepar.zx80.machine.tape.TapBlock
import ru.alepar.zx80.machine.tape.TapTapeFile
import ru.alepar.zx80.machine.tape.TapeDeck
import ru.alepar.zx80.machine.tape.TapePulser
import ru.alepar.zx80.machine.tape.TzxTapeFile
import ru.alepar.zx80.machine.tape.TzxTurboData

/**
 * Score harness suite for the pulse-level tape loading path.
 *
 * Verifies that [TapePulser] produces correct EAR-pin waveforms and that the [TapeDeck] correctly
 * drives pulse-mode for both standard and turbo blocks. No full CPU run is needed here — the unit
 * checks exercise the [TapeDeck.startPulseBlock] / [TapeDeck.earLevel] API directly.
 *
 * Details shape: { "checks": [ { "name": "...", "passed": bool }, ... ] }
 */
class TapePulseLoadSuite : Suite {
    override val name: String = "tape-pulse-load"
    override val weight: Double = 0.05

    private data class Check(val name: String, val passed: Boolean)

    override fun run(): SuiteResult {
        val checks = mutableListOf<Check>()

        fun check(name: String, block: () -> Boolean) {
            val ok =
                try {
                    block()
                } catch (_: Exception) {
                    false
                }
            checks.add(Check(name, ok))
        }

        // 1. No tape loaded → earLevel returns idle-low (0)
        check("no-tape-ear-idle-low") {
            val deck = TapeDeck()
            deck.earLevel(0L) == 0
        }

        // 2. startPulseBlock on a .tap block engages the pulser
        check("tap-block-engages-pulser") {
            val deck = TapeDeck()
            deck.loadTape(
                TapTapeFile(
                    listOf(TapBlock(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x55.toByte())))
                )
            )
            deck.startPulseBlock(0L)
            deck.pulser != null
        }

        // 3. Pilot tone is driven at the correct half-period via earLevel
        check("pilot-tone-correct-half-period") {
            val deck = TapeDeck()
            deck.loadTape(TapTapeFile(listOf(TapBlock(byteArrayOf(0x00, 0x01, 0x01)))))
            deck.startPulseBlock(0L)
            // At t=0 → first pilot half (high = 0x40)
            val atZero = deck.earLevel(0L)
            // At t=2167 → still first half
            val beforeEdge = deck.earLevel(2167L)
            // At t=2168 → second half (low = 0)
            val atEdge = deck.earLevel(2168L)
            atZero == 0x40 && beforeEdge == 0x40 && atEdge == 0
        }

        // 4. turbo block (.tzx 0x11) uses its custom timing parameters
        check("tzx-turbo-block-custom-timing") {
            val deck = TapeDeck()
            val customPilot = 3000 // non-standard pilot half-period
            deck.loadTape(
                TzxTapeFile(
                    listOf(
                        TzxTurboData(
                            pilotPulse = customPilot,
                            sync1Pulse = 667,
                            sync2Pulse = 735,
                            zeroBitPulse = 855,
                            oneBitPulse = 1710,
                            pilotToneLen = 100,
                            lastByteBits = 8,
                            pauseMs = 0,
                            data = byteArrayOf(0xFF.toByte(), 0x42, (0xFF xor 0x42).toByte()),
                        )
                    )
                )
            )
            deck.startPulseBlock(0L)
            // At t=customPilot-1 → still first pilot half (high)
            val beforeEdge = deck.earLevel((customPilot - 1).toLong())
            // At t=customPilot → second pilot half (low)
            val atEdge = deck.earLevel(customPilot.toLong())
            beforeEdge == 0x40 && atEdge == 0
        }

        // 5. stopPulse clears the pulser and restores idle-low
        check("stop-pulse-restores-idle") {
            val deck = TapeDeck()
            deck.loadTape(TapTapeFile(listOf(TapBlock(byteArrayOf(0x00, 0x01, 0x01)))))
            deck.startPulseBlock(0L)
            deck.stopPulse()
            deck.pulser == null && deck.earLevel(0L) == 0
        }

        // 6. earLevel after tape data ends returns 0 (pulse stream complete)
        check("ear-low-after-block-data") {
            val deck = TapeDeck()
            // Small 1-byte block with 1-bit turbo timing (pilotToneLen=10, custom short timing)
            deck.loadTape(
                TzxTapeFile(
                    listOf(
                        TzxTurboData(
                            pilotPulse = 10,
                            sync1Pulse = 5,
                            sync2Pulse = 5,
                            zeroBitPulse = 8,
                            oneBitPulse = 16,
                            pilotToneLen = 4,
                            lastByteBits = 8,
                            pauseMs = 0,
                            data = byteArrayOf(0x00),
                        )
                    )
                )
            )
            deck.startPulseBlock(0L)
            // pilot: 4 half-periods × 10 = 40 T-states
            // sync: 5+5 = 10 T-states
            // data: 8 bits of 0x00, each 0-bit = 2×8 = 16 T-states, total 128 T-states
            // Then trailing edge (or end). Total data end = 40+10+128 = 178
            val afterBlock = 300L
            deck.earLevel(afterBlock) == 0
        }

        // 7. start tstate offset works via deck.startPulseBlock
        check("start-tstate-offset-via-deck") {
            val offset = 100_000L
            val deck = TapeDeck()
            deck.loadTape(
                TapTapeFile(
                    listOf(TapBlock(byteArrayOf(0xFF.toByte(), 0xAA.toByte(), 0x55.toByte())))
                )
            )
            deck.startPulseBlock(offset)
            // Before offset: low (pulser started but no edges have fired yet)
            val before = deck.earLevel(offset - 1)
            // At offset: first pilot high (first edge fired)
            val atStart = deck.earLevel(offset)
            before == 0 && atStart == 0x40
        }

        // 8. Pulser data matches TapePulser unit test values for 0-bit half-period
        check("zero-bit-half-period-855-via-deck") {
            val deck = TapeDeck()
            deck.loadTape(TapTapeFile(listOf(TapBlock(byteArrayOf(0x00, 0x00, 0x00)))))
            deck.startPulseBlock(0L)
            val dataStart = 8063L * 2168L + 667L + 735L
            val firstHalfEnd = dataStart + 855L
            // First half of bit is LOW (EAR = 0), second half HIGH
            deck.earLevel(dataStart) == 0 && deck.earLevel(firstHalfEnd) == 0x40
        }

        // 9. Pulser data matches for 1-bit half-period.
        // Block: flag=0x80 (MSB=1 → first transmitted bit is a 1-bit), payload=0x00, parity=0x80.
        // flag != 0xFF → deck selects 8063-pulse pilot, consistent with dataStart below.
        check("one-bit-half-period-1710-via-deck") {
            val deck = TapeDeck()
            deck.loadTape(
                TapTapeFile(listOf(TapBlock(byteArrayOf(0x80.toByte(), 0x00, 0x80.toByte()))))
            )
            deck.startPulseBlock(0L)
            val dataStart = 8063L * 2168L + 667L + 735L
            val firstHalfEnd = dataStart + 1710L
            // First half of 1-bit (flag byte MSB=1) is LOW, second half HIGH
            deck.earLevel(dataStart) == 0 && deck.earLevel(firstHalfEnd) == 0x40
        }

        // 10. trapEnabled=false does not affect pulse mode (pulse is independent)
        check("trap-disabled-does-not-block-pulse") {
            val deck = TapeDeck()
            deck.loadTape(TapTapeFile(listOf(TapBlock(byteArrayOf(0x00, 0x01, 0x01)))))
            deck.trapEnabled = false
            deck.startPulseBlock(0L)
            // Pulse mode should still engage even when trap is disabled
            deck.pulser != null && deck.earLevel(0L) == 0x40
        }

        val passed = checks.count { it.passed }
        val details = buildJsonObject {
            put(
                "checks",
                JsonArray(
                    checks.map { c ->
                        buildJsonObject {
                            put("name", JsonPrimitive(c.name))
                            put("passed", JsonPrimitive(c.passed))
                        }
                            as JsonObject
                    }
                ),
            )
        }
        return SuiteResult(
            name = name,
            weight = weight,
            passed = passed,
            total = checks.size,
            details = details,
        )
    }
}
