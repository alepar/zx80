# M2.9: Boots-to-BASIC Sweep + Score Harness Extension — Design

## Goal

Wrap up M2 with a composite "boots-to-BASIC" milestone gate. After
`Spectrum48k().reset()` the gate verifies, in order:

Run the machine for `RUN_FRAMES = 500` frames after reset, then assert
end-state:

1. **boot-completed** — `mem.read(0x5C78) >= 100` at end. The real ROM
   reaches EI at frame ~83 and the ISR increments FRAMES once per
   frame, so by frame 500 the counter is ~417. A floor of 100 rejects
   the well-known false positive: the ROM's init data-table writes
   `FRAMES = 2` at frame ~20 (a quirk where ROM bytes happen to look
   like FRAMES setup), then resets to 0 at frame 32, then real ISR
   takes over at frame ~83. `>= 100` is well above the false-positive
   ceiling.
2. **frames-incrementing** — sample FRAMES at frames 400 and 500;
   assert the value increased by >= 50. Proves the ISR is *currently*
   active, not just that it ran at some point and stopped.
3. **screen-drawn** — at end of run, screen RAM (0x4000-0x57FF) has
   at least one non-zero byte. The Spectrum © message is first
   written around frame 350, so a 500-frame budget covers it
   comfortably.

The original `cpu.iff1` post-frame check (fixed in prior patch) was
flawed because `runFrame()` ends with `interruptRequest()` which clears
`iff1` on INT ack. The naive `FRAMES > 0` check (this patch fixes) was
ALSO flawed because of the frame-20 init-table false positive.

Plumb the gate into the score harness as a new `BootsToBasic` Suite,
normalizing the composite score formula so adding suites doesn't push
the ceiling above 1.0. Tag `m2-spectrum-machine`.

## Context

Beads issue: `zx80-lyo` (M2.9 rollup).

Pre-M2.9 state:
- M2.1-M2.8 complete; tags `m2-phase01-1` through `m2-phase01-8` on
  origin.
- Phase H + M1 residuals: SCORE 0.998, FUSE 1356/1356, programs 5/5,
  ZEXDOC clean.
- `zx80-1qc` closed (not a bug; the 50-frame gate was wrong). The
  diagnosis report at
  `docs/superpowers/investigations/2026-05-10-zx80-1qc-diagnosis.md`
  pins the real ROM boot timing: ~82 frames for the memory test, then
  EI, then FRAMES starts incrementing.
- `zx80-o41` (corrected gate) will be closed by M2.9.

Current Score harness in `harness/Score.kt`:
```kotlin
val score = results.sumOf { it.weight * it.ratio }
```
Suites today (and weights):
- `OpcodeCoverage` — 0.2
- `FuseSuite` — 0.7
- `ProgramsSuite` — 0.1
- Total: 1.0

Naïvely adding a fourth suite at weight 0.1 makes total weight 1.1 and
ceiling SCORE 1.1. Cleaner: normalize the composite by total weight so
the ceiling stays 1.0 regardless of how many suites we add.

## Scope

### M2.9-A: BootsToBasic Suite + tests

New `src/main/kotlin/ru/alepar/zx80/harness/suites/BootsToBasic.kt`:

```kotlin
package ru.alepar.zx80.harness.suites

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.harness.SuiteResult
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.UlaRenderer

/**
 * End-to-end "did the Spectrum 48K ROM boot?" gate, run as a Suite for the score harness.
 *
 * Runs the machine for RUN_FRAMES frames after reset, samples FRAMES counter at
 * SAMPLE_FRAME (mid-run), then asserts end-state. Three sub-checks:
 *
 *   1. boot-completed — mem.read(0x5C78) >= FRAMES_FLOOR at end. The ROM reaches EI
 *      at frame ~83; ISR ticks FRAMES once per frame; by RUN_FRAMES=500 the counter
 *      is ~417. FRAMES_FLOOR=100 rejects a known false positive: ROM init writes
 *      FRAMES=2 at frame ~20 from a data table, then resets to 0 at frame 32, then
 *      the real ISR takes over at frame 83.
 *   2. frames-incrementing — sampled at SAMPLE_FRAME (400) and at end (500); end
 *      value must exceed the mid-run sample by >= INC_THRESHOLD (50). Proves ISR
 *      is currently active.
 *   3. screen-drawn — screen RAM (0x4000-0x57FF) has at least one non-zero byte
 *      at end of run. © message is first written around frame 350; 500 covers it.
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

        val checks = listOf(
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
        return SuiteResult(name = name, weight = weight, passed = passed, total = checks.size,
            details = details)
    }

    private fun jsonCheck(label: String, ok: Boolean): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(label))
        put("passed", JsonPrimitive(ok))
    }

    companion object {
        const val RUN_FRAMES = 500           // memory test ~82 + EI at ~83 + ISR running thereafter
        const val SAMPLE_FRAME = 400         // mid-run snapshot for incrementing check
        const val FRAMES_FLOOR = 100         // end value must exceed this (rejects frame-20 false positive of 2)
        const val INC_THRESHOLD = 50         // FRAMES must grow by this much between SAMPLE and end
    }
}
```

Tests in `src/test/kotlin/ru/alepar/zx80/harness/suites/BootsToBasicTest.kt` — 4 assertions:

```kotlin
@Test
fun `Suite runs without throwing on a fresh decoder`() {
    val suite = BootsToBasic(OpTableBuilder.build())
    val result = suite.run()
    assertThat(result.passed).isBetween(0, 3)
    assertThat(result.total).isEqualTo(3)
}

@Test
fun `All three sub-checks pass on production decoder (gold path)`() {
    val suite = BootsToBasic(OpTableBuilder.build())
    val result = suite.run()
    assertThat(result.passed).isEqualTo(3)
}

@Test
fun `Result has weight 0_1 and name boots-to-basic`() {
    val result = BootsToBasic(OpTableBuilder.build()).run()
    assertThat(result.name).isEqualTo("boots-to-basic")
    assertThat(result.weight).isEqualTo(0.1)
}

@Test
fun `Details include frames-mid and frames-end fields`() {
    val result = BootsToBasic(OpTableBuilder.build()).run()
    val details = result.details
    assertThat(details).containsKey("frames-mid")
    assertThat(details).containsKey("frames-end")
    assertThat(details).containsKey("checks")
}
```

### M2.9-B: Score normalization + suite registration

Modify `src/main/kotlin/ru/alepar/zx80/harness/Score.kt`. The current
formula is `results.sumOf { it.weight * it.ratio }`. Change to a
normalized weighted average:

```kotlin
object Score {
    fun compute(results: List<SuiteResult>): CompositeScore {
        val totalWeight = results.sumOf { it.weight }
        val score = if (totalWeight > 0.0) {
            results.sumOf { it.weight * it.ratio } / totalWeight
        } else 0.0
        return CompositeScore(score, results)
    }
}
```

Why normalize: today's weights (0.2 + 0.7 + 0.1) happen to total 1.0,
so the un-normalized formula gives a 0..1 SCORE. Adding suites is
expected to be ongoing; normalization makes weight an *intent*
(relative importance), not a constraint (must-sum-to-1.0). Each new
suite picks a weight per its relative importance; the harness handles
the math.

This change keeps the post-M2.8 baseline approximately unchanged: with
weights 0.2/0.7/0.1, SCORE 0.998 → normalized 0.998 (since total
weight was already 1.0). Adding boots-to-basic at 0.1 makes total
weight 1.1; if all suites pass perfectly the SCORE remains 1.0 (not
1.1).

Modify `src/main/kotlin/ru/alepar/zx80/cli/ScoreCommand.kt` to register
the new suite in the suites list:

```kotlin
val suites = listOf(
    OpcodeCoverage(decoder),
    FuseSuite(...),
    ProgramsSuite(decoder, ResourceLoader.loadPrograms()),
    BootsToBasic(decoder),   // NEW
)
```

Existing tests for Score / suites should still pass after the
normalization change (since 1.0 / 1.0 = 1.0 for the existing weights).
But there may be a CompositeScoreTest that asserts the SUMMED score;
update it to assert the normalized value.

### M2.9-C: Sweep + tag + close M2 epic

Standard sweep with strict gates:

1. `./gradlew clean check installDist` BUILD SUCCESSFUL.
2. All new tests pass.
3. Existing tests still green (Score formula change is backward
   compatible for current weights).
4. **`./build/install/zx80/bin/zx80 score` reports boots-to-basic: 3/3
   PASS.**
5. FUSE: 1356/1356.
6. ZEXDOC: clean.
7. Programs: 5/5.
8. Composite SCORE: ≥ 0.998 (the normalization should preserve this
   exactly when all four suites are at their current pass rates; with
   boots-to-basic 3/3 the score may tick up).

Tag: **`m2-spectrum-machine`** (the M2 milestone).

Close:
- `zx80-lyo` (M2.9 rollup) — done.
- `zx80-o41` (corrected FRAMES gate spec) — superseded by the
  BootsToBasic suite's RUN_FRAMES handling, which uses an end-state
  snapshot at frame 500 rather than a specific intermediate check.
- `zx80-48f` (M2 epic) — done.

### Out of scope

- Pixel-perfect screen comparison vs reference PNG (M3 regression
  suite material).
- Keypress integration tests (real keyboard input wired to BASIC).
- Audio integration check (does BEEP produce audible output) — manual
  smoke only; no JUnit gate.
- Border-color drift tests.
- Mid-frame border / contention tests (M2.7).
- M2.7 itself (still open beads `zx80-sj5`, deferred to a future
  milestone).

## Architecture

```
src/main/kotlin/ru/alepar/zx80/harness/
  Score.kt                       MODIFY (normalize composite)
  suites/BootsToBasic.kt         NEW
src/main/kotlin/ru/alepar/zx80/cli/
  ScoreCommand.kt                MODIFY (register new suite)
src/test/kotlin/ru/alepar/zx80/harness/
  suites/BootsToBasicTest.kt     NEW (4 assertions)
  ScoreTest.kt or similar        UPDATE if existing tests assert
                                  un-normalized totals
```

No machine-layer changes. The Suite uses the existing public Spectrum48k
API (`reset`, `runFrame`, `cpu.iff1`, `mem.read`).

**Performance:** the Suite runs up to 260 frames (~13.6M T-states).
At our JVM test execution speed (rough order of 50M T-states/sec
single-threaded), that's ~270ms wall time. Acceptable for a once-per-
`zx80 score` run; minor cost in CI.

**Determinism:** Spectrum48k is deterministic given the same ROM and
reset state. The Suite's outcome is the same on every run unless
emulator behavior changes.

## Test strategy

About 4 new assertions in BootsToBasicTest. May add a 5th assertion
to a (existing? new?) Score test verifying normalization. Test names
use commas/dashes — NEVER colons in backtick-quoted Kotlin test
methods.

## Validation gates (M2.9-C)

1. `./gradlew clean check installDist` BUILD SUCCESSFUL.
2. BootsToBasicTest passes (4 assertions).
3. Existing tests still green.
4. FUSE: 1356/1356.
5. ZEXDOC clean. Programs 5/5.
6. boots-to-basic: 3/3 PASS in the score harness JSON output.
7. Composite SCORE: ≥ 0.998.

Tag: `m2-spectrum-machine`.

## Work-unit breakdown (per-task beads pattern)

| WU | Description |
|---|---|
| M2.9-A | `BootsToBasic` Suite class + `BootsToBasicTest` (4 assertions). |
| M2.9-B | Normalize Score composite formula (sum / totalWeight); register BootsToBasic in ScoreCommand's suite list; update any test that asserted un-normalized totals. |
| M2.9-C | Sweep + tag `m2-spectrum-machine` + close `zx80-lyo`, `zx80-o41`, `zx80-48f` + push. |

Within-phase deps: A → B (B references the new class). C depends on
both. Three small WUs.

## Risks

- **RUN_FRAMES budget.** Memory test ~82 frames + real EI at ~83 +
  screen © drawn at ~350. RUN_FRAMES=500 gives comfortable headroom
  (~150 frames of margin past the screen draw). Future RAM-size
  changes or BASIC ROM variant could push this higher; bump if needed.
- **FRAMES counter false positive at frame 20.** The ROM writes
  FRAMES_lo=2 from an init data table around frame 20, then resets
  to 0 at frame 32, then real ISR takes over at frame 83. A naive
  `FRAMES > 0` check would fire on the frame-20 false positive.
  Mitigated by checking FRAMES >= FRAMES_FLOOR=100 at end (well above
  the noise floor of 2).
- **Score normalization breaks downstream consumers.** The JSON shape
  is unchanged (CompositeScore still has `score` field 0..1); the
  *value* of `score` is the normalized weighted average instead of the
  raw sum. For weights that already total 1.0 (today's three suites),
  these are equal — no change. After adding the fourth, the
  normalization keeps SCORE in 0..1.
- **Screen-non-empty too loose.** Any non-zero byte passes the gate.
  If a future change populates screen RAM during reset, the gate
  passes spuriously. Mitigated by also requiring boot-completed +
  frames-incrementing; spurious screen-drawn alone is 1/3, not 3/3.
- **Slow Suite.** ~270ms per `zx80 score` run. Negligible.
- **Test method names with colons.** Recurring author trap. All test
  names in this spec use commas/dashes/underscores; no colons.
