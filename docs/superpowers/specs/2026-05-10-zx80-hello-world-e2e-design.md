# HELLO WORLD End-to-End Integration Test — Design

## Goal

A JUnit integration test that boots the Spectrum 48K ROM, types
`PRINT "HELLO, WORLD"` via simulated keyboard input, runs the BASIC
line, scrapes the rendered screen via 8×8 pixel-cell font matching,
and asserts the substring `HELLO, WORLD` appears.

End-to-end exercise of: CPU correctness, IoBus, Keyboard, FrameScheduler,
ROM key-scan ISR, BASIC interpreter, PRINT command, character ROM, and
screen rendering. Catches bugs in any layer in a single test.

## Context

After M2.9 the `BootsToBasic` Suite verifies the ROM boots in a coarse
sense (ISR runs, FRAMES counter increments, screen non-empty). This
test goes one step further: prove the emulated machine is usable as a
1982 ZX Spectrum by interactively executing a BASIC statement.

The test is intentionally outside the score harness — it's slow
(~6 sec wall time per run) and binary pass/fail; the score harness
prefers fast, gradient-friendly suites.

Spectrum BASIC keyboard quirks the test relies on:
- At BASIC ready the cursor is in **K** (Keyword) mode. Pressing letter
  keys emits BASIC keywords. Specifically, pressing `P` emits `PRINT `
  and switches to L (Letter) mode.
- Once in L mode, letters emit literally.
- `"` is `SYMBOL_SHIFT + P`.
- `,` is `SYMBOL_SHIFT + N`.
- ENTER submits the line; BASIC parses + executes; output appears on
  screen; cursor returns to K mode ready for the next line.

Sequence to type: P, SYMSHIFT+P, H, E, L, L, O, SYMSHIFT+N, SPACE,
W, O, R, L, D, SYMSHIFT+P, ENTER — 16 keystrokes.

ROM keyboard scan and debounce: the 50Hz INT ISR scans the matrix
once per frame; the ROM expects keys to be held for at least 1 frame
and released for at least 2-3 frames before treating the next press
as new. Safe timing: 5 frames held + 5 frames released per keystroke.

## Scope

### hello-A: SpectrumTypist helper

New `src/test/kotlin/ru/alepar/zx80/integration/SpectrumTypist.kt`:

```kotlin
package ru.alepar.zx80.integration

import ru.alepar.zx80.machine.Keyboard
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.SpectrumKey

/**
 * Simulates a human typing on the Spectrum keyboard. Each press holds the key for [framesHeld]
 * frames (so the ROM's 50Hz key-scan ISR samples it), releases, then idles for [framesReleased]
 * before the next press (so the ROM's debounce treats the next press as new).
 *
 * Test-only utility; lives under `src/test/`.
 */
class SpectrumTypist(
    private val machine: Spectrum48k,
    private val keyboard: Keyboard,
    private val framesHeld: Int = 5,
    private val framesReleased: Int = 5,
) {
    /** Press and release a single key. */
    fun press(key: SpectrumKey) {
        keyboard.press(key)
        repeat(framesHeld) { machine.runFrame() }
        keyboard.release(key)
        repeat(framesReleased) { machine.runFrame() }
    }

    /** Press a key while a modifier (CAPS_SHIFT or SYMBOL_SHIFT) is held. */
    fun withMod(modifier: SpectrumKey, key: SpectrumKey) {
        keyboard.press(modifier)
        keyboard.press(key)
        repeat(framesHeld) { machine.runFrame() }
        keyboard.release(key)
        keyboard.release(modifier)
        repeat(framesReleased) { machine.runFrame() }
    }

    fun enter() = press(SpectrumKey.ENTER)
    fun space() = press(SpectrumKey.SPACE)
}
```

### hello-B: ScreenReader helper

New `src/test/kotlin/ru/alepar/zx80/integration/ScreenReader.kt`:

```kotlin
package ru.alepar.zx80.integration

import ru.alepar.zx80.cpu.Memory

/**
 * Reads the Spectrum's 256x192 screen as text by matching each 8x8 character cell against
 * the character ROM at 0x3D00. Each ASCII char (0x20..0x7F) has an 8-byte glyph at
 * 0x3D00 + 8*(charCode - 0x20).
 *
 * `null` is returned for cells that don't exactly match any glyph (graphic chars,
 * user-defined chars, blank-but-non-paper-styled cells). The grid is 32 columns x 24 rows.
 *
 * Uses the same row-interleave formula as UlaRenderer to find each pixel-row's screen-RAM
 * address.
 */
class ScreenReader(private val mem: Memory) {

    /** Returns the matched ASCII char (0x20..0x7F) or null if no exact glyph match. */
    fun cellChar(row: Int, col: Int): Char? {
        require(row in 0 until 24) { "row $row out of range" }
        require(col in 0 until 32) { "col $col out of range" }
        val cellBytes = readCellBytes(row, col)
        for (charCode in 0x20..0x7F) {
            val romOffset = 0x3D00 + (charCode - 0x20) * 8
            var match = true
            for (i in 0..7) {
                if (mem.read(romOffset + i) != cellBytes[i]) {
                    match = false
                    break
                }
            }
            if (match) return charCode.toChar()
        }
        return null
    }

    /** 24 rows, 32 chars per row. Unmatched cells become '?'. */
    fun asText(): List<String> = (0 until 24).map { row ->
        (0 until 32).map { col -> cellChar(row, col) ?: '?' }.joinToString("")
    }

    /** True if any row contains [needle]. */
    fun contains(needle: String): Boolean = asText().any { it.contains(needle) }

    private fun readCellBytes(row: Int, col: Int): IntArray {
        val bytes = IntArray(8)
        for (i in 0..7) {
            val y = row * 8 + i
            // Row-interleave formula (matches UlaRenderer):
            val rowBase = 0x4000 or
                ((y and 0xC0) shl 5) or
                ((y and 0x07) shl 8) or
                ((y and 0x38) shl 2)
            bytes[i] = mem.read(rowBase + col)
        }
        return bytes
    }
}
```

### hello-C: BasicPrintHelloWorldTest

New `src/test/kotlin/ru/alepar/zx80/integration/BasicPrintHelloWorldTest.kt`:

```kotlin
package ru.alepar.zx80.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import ru.alepar.zx80.machine.Beeper
import ru.alepar.zx80.machine.BorderState
import ru.alepar.zx80.machine.Keyboard
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.SpectrumIoBus
import ru.alepar.zx80.machine.SpectrumKey

class BasicPrintHelloWorldTest {

    @Test
    @Tag("integration")
    fun `BASIC PRINT HELLO WORLD prints the string to the screen`() {
        val machine = Spectrum48k()
        val keyboard = Keyboard()
        machine.cpu.io = SpectrumIoBus(keyboard, Beeper(machine.cpu), BorderState())
        machine.reset()

        // Boot: ROM memory test (~82 frames) + initial © draw. 200 is comfortable headroom.
        repeat(200) { machine.runFrame() }

        val type = SpectrumTypist(machine, keyboard)
        type.press(SpectrumKey.P)                                  // PRINT keyword
        type.withMod(SpectrumKey.SYMBOL_SHIFT, SpectrumKey.P)      // "
        type.press(SpectrumKey.H)
        type.press(SpectrumKey.E)
        type.press(SpectrumKey.L)
        type.press(SpectrumKey.L)
        type.press(SpectrumKey.O)
        type.withMod(SpectrumKey.SYMBOL_SHIFT, SpectrumKey.N)      // ,
        type.space()
        type.press(SpectrumKey.W)
        type.press(SpectrumKey.O)
        type.press(SpectrumKey.R)
        type.press(SpectrumKey.L)
        type.press(SpectrumKey.D)
        type.withMod(SpectrumKey.SYMBOL_SHIFT, SpectrumKey.P)      // "
        type.enter()

        // BASIC parses + executes + reprints ready prompt.
        repeat(30) { machine.runFrame() }

        val reader = ScreenReader(machine.mem)
        val screenText = reader.asText().joinToString("\n")
        assertThat(reader.contains("HELLO, WORLD"))
            .`as`("expected screen contents to contain HELLO, WORLD; actual screen:\n$screenText")
            .isTrue
    }
}
```

Total emulated frames: 200 (boot) + 16 keystrokes × 10 frames + 30
(execution) ≈ 390 frames ≈ 7.8 emulated seconds. Wall time at our
test JVM speed is roughly 6-8 seconds.

### hello-D: Sweep + tag

Standard sweep with full validation gates:

1. `./gradlew clean check installDist` BUILD SUCCESSFUL.
2. New BasicPrintHelloWorldTest passes.
3. Existing tests still pass.
4. FUSE: 1356/1356.
5. ZEXDOC: clean.
6. Programs: 5/5.
7. Composite SCORE: ≥ 0.998.

Tag: `m2-hello-world` — a strict end-to-end gate beyond `m2-spectrum-machine`.

### Out of scope

- Testing other BASIC programs (`FOR..NEXT`, `IF..THEN`, etc.) —
  follow-up suite if HELLO WORLD reveals bugs.
- Cycle-accurate keyboard scan timing (M2.7 contention; not needed).
- BASIC error cases (`Nonsense in BASIC`, syntax errors).
- Keyword auto-completion on alternative keys (`L` for LET, etc.).
- Tape loading (M3).
- A `SpectrumTypist.text("HELLO")` convenience overload that maps
  ASCII chars to SpectrumKey sequences — easy to add later if more
  E2E tests come.

## Architecture

```
src/test/kotlin/ru/alepar/zx80/integration/
  SpectrumTypist.kt              NEW (test helper)
  ScreenReader.kt                NEW (test helper)
  BasicPrintHelloWorldTest.kt    NEW (the integration test)
```

No production-code changes. The test composes existing public APIs
(Spectrum48k, Keyboard, SpectrumIoBus, Beeper, BorderState).

**Why a new `integration/` package**: marks these as slow E2E tests vs
the rest of the unit-test suite. The `@Tag("integration")` annotation
on the test method lets future CI configure `useJUnitPlatform { includeTags / excludeTags }`
if needed; for now all tests run together.

**Threading**: tests are single-threaded; no Pacer involved.
`machine.runFrame()` runs synchronously on the test thread.

**Memory access**: `ScreenReader.cellChar` uses 64 reads per cell ×
768 cells × N font candidates = up to 3.6M `mem.read` calls in the
worst case (all glyphs scanned for all cells). In practice most cells
either match early or are blank; should run in milliseconds.

## Test strategy

The integration test IS the strategy. No unit tests for the helpers
themselves — `SpectrumTypist` is < 20 lines and `ScreenReader` is
small enough that bugs in either manifest as the integration test
failing.

If the test fails, the assertion message embeds the full screen-as-
text grid, so failures are diagnosable without re-running.

Test name uses commas, not colons (Kotlin requirement; recurring
spec-author trap).

## Validation gates (hello-D)

1. `./gradlew clean check installDist` BUILD SUCCESSFUL.
2. BasicPrintHelloWorldTest passes.
3. Existing tests still pass.
4. FUSE: 1356/1356.
5. ZEXDOC: clean.
6. Programs: 5/5.
7. Composite SCORE: ≥ 0.998.

Tag: `m2-hello-world`.

## Work-unit breakdown (per-task beads pattern)

| WU | Description |
|---|---|
| hello-A | `SpectrumTypist` test helper. Independent. |
| hello-B | `ScreenReader` test helper. Independent. |
| hello-C | `BasicPrintHelloWorldTest` integration test composing A+B. Depends on A+B. |
| hello-D | Sweep + tag `m2-hello-world` + push. Depends on C. |

Within-phase deps: A and B independent. C depends on both. D depends
on C. Linear order recommended for clarity.

## Risks

- **Keyboard timing.** Default 5/5 frames may be off. If the ROM
  misses presses, increase `framesHeld`; if it sees multi-press,
  increase `framesReleased`. Each is a constructor knob, easy to
  tune from the test if needed. Initial gut feel: 5/5 is comfortable
  given the ROM's 50Hz scan rate.
- **Cursor mode at BASIC ready.** Pre-condition: cursor is in K mode
  after the 200-frame boot. The Sinclair ROM should land here
  reliably. If not, first `P` press won't emit `PRINT` — the test
  fails with the screen-as-text grid in the assertion, showing what
  WAS typed.
- **Font location.** Spectrum 48K character ROM is at 0x3D00-0x3FFF;
  `8 * (charCode - 0x20)` gives the per-glyph offset. Confirmed by
  Sinclair docs and our M2.3 spec. If the bundled `48.rom` somehow
  has a different layout, every cell would unmatched-default to `?`
  and the assertion would fail loudly.
- **Spurious char matches.** ASCII chars at 0x20..0x7F (96 glyphs).
  Exact 8-byte equality means a cell only matches one glyph at most.
  False positives unlikely; false negatives (e.g., inverse video,
  attribute styling) → cell shows `?`. Mitigated by searching the
  whole 32×24 grid for the substring; one row matching is enough.
- **Slow CI.** 6-8 sec wall time per execution. `@Tag("integration")`
  lets CI exclude/separate slow tests if needed.
- **Test name colons.** Kotlin forbids `:` in backtick-quoted method
  names. All names in this spec use commas/spaces; no colons.
- **Surfaces new bugs.** This test exercises code paths the unit suite
  doesn't (real ROM running 7+ seconds; keyboard ISR; BASIC parser).
  Expect at least one debug iteration; the screen-as-text in the
  assertion is the debugging primitive.
