package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.harness.SuiteResult
import ru.alepar.zx80.machine.Spectrum48k

/**
 * End-to-end "did the Spectrum 48K ROM boot?" gate, run as a Suite for the score harness.
 *
 * Runs the machine for RUN_FRAMES frames after reset, samples FRAMES counter at SAMPLE_FRAME
 * (mid-run), then asserts end-state. Three sub-checks:
 * 1. boot-completed — mem.read(0x5C78) >= FRAMES_FLOOR at end. The ROM reaches EI at frame ~83; ISR
 *    ticks FRAMES once per frame; by RUN_FRAMES=500 the counter is ~417. FRAMES_FLOOR=100 rejects a
 *    known false positive: ROM init writes FRAMES=2 at frame ~20 from a data table, then resets to
 *    0 at frame 32, then the real ISR takes over at frame 83.
 * 2. frames-incrementing — sampled at SAMPLE_FRAME (400) and at end (500); end value must exceed
 *    the mid-run sample by >= INC_THRESHOLD (50). Proves ISR is currently active.
 * 3. screen-drawn — screen RAM (0x4000-0x57FF) has at least one non-zero byte at end of run. ©
 *    message is first written around frame 350; 500 covers it.
 *
 * Returns SuiteResult with passed = count of checks that succeeded (0..3), total = 3.
 */
class BootsToBasic(private val decoder: Decoder) : Suite {
    override val name: String = "boots-to-basic"
    override val weight: Double = 0.1

    override fun run(): SuiteResult {
        val machine = Spectrum48k(decoder)
        machine.reset()

        var framesMid = 0
        for (i in 1..SAMPLE_FRAME) {
            machine.runFrame()
        }
        framesMid = machine.mem.read(0x5C78)
        for (i in (SAMPLE_FRAME + 1)..RUN_FRAMES) {
            machine.runFrame()
        }
        val framesEnd = machine.mem.read(0x5C78)

        val bootCompleted = framesEnd >= FRAMES_FLOOR
        val framesIncrementing = (framesEnd - framesMid) >= INC_THRESHOLD

        var screenNonEmpty = false
        for (addr in 0x4000..0x57FF) {
            if (machine.mem.read(addr) != 0) {
                screenNonEmpty = true
                break
            }
        }

        val checks =
            listOf(
                "boot-completed" to bootCompleted,
                "frames-incrementing" to framesIncrementing,
                "screen-drawn" to screenNonEmpty,
            )
        val passed = checks.count { it.second }

        val details = buildJsonObject {
            put("checks", JsonArray(checks.map { (label, ok) -> jsonCheck(label, ok) }))
            put("frames-mid", JsonPrimitive(framesMid))
            put("frames-end", JsonPrimitive(framesEnd))
        }
        return SuiteResult(
            name = name,
            weight = weight,
            passed = passed,
            total = checks.size,
            details = details,
        )
    }

    private fun jsonCheck(label: String, ok: Boolean): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(label))
        put("passed", JsonPrimitive(ok))
    }

    companion object {
        const val RUN_FRAMES = 500 // memory test ~82 + EI at ~83 + ISR running thereafter
        const val SAMPLE_FRAME = 400 // mid-run snapshot for incrementing check
        const val FRAMES_FLOOR =
            100 // end value must exceed this (rejects frame-20 false positive of 2)
        const val INC_THRESHOLD = 50 // FRAMES must grow by this much between SAMPLE and end
    }
}
