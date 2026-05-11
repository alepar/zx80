# M2.8: Border Color via OUT(0xFE) low 3 bits — Design

## Goal

Capture bits 0-2 of OUT(0xFE) as a 3-bit Spectrum border color, render a
48-pixel border around the existing 256x192 framebuffer to produce a
352x288 output image. No mid-frame border changes — single color per
frame, latest write wins. SpectrumIoBus.write becomes fully wired
(keyboard from M2.5, beeper from M2.6, border from M2.8).

## Context

Beads issue: `zx80-xbf` (parent: `zx80-48f` M2 epic).

After Phase H, opcode coverage is 1776/1792 and composite SCORE is 0.997
— but the visible window still shows only the 256x192 screen area. M2.8
finishes the visible canvas by drawing the standard Spectrum border.

Spectrum hardware: writes to any port with A0=0 hit the ULA. Bits 0-2 of
the written byte set the 3-bit border color (one of the 8 Spectrum
palette entries; always non-bright). Bit 3 is MIC (out-of-scope), bit 4
is the beeper (M2.6), bits 5-7 unused.

Mid-frame border changes (e.g., loading-screen colored stripes) require
per-line border tracking similar to Beeper's event log. YAGNI for M2.8 —
the M2.8 design renders the single latest written color per frame.

## Scope

### M2.8-A: BorderState class

New `src/main/kotlin/ru/alepar/zx80/machine/BorderState.kt`:

```kotlin
package ru.alepar.zx80.machine

/**
 * Current Spectrum border color (3 bits, 0..7). Pacer-thread writer via SpectrumIoBus.write;
 * EDT reader via BorderedUlaRenderer.render. Volatile is sufficient — int writes/reads are
 * atomic on JVM, and volatile ensures cross-thread visibility.
 *
 * Initial color is 0 (black). Border is always non-bright on real Spectrum hardware.
 */
class BorderState {
    @Volatile private var colorValue: Int = 0

    fun write(value: Int) {
        colorValue = value and 0x07
    }

    fun read(): Int = colorValue
}
```

Tests (`BorderStateTest`) — 3 assertions:
- `fresh BorderState reads as 0`
- `write(7) followed by read returns 7`
- `write(0xFF) masks to low 3 bits and returns 7`

### M2.8-B: BorderedUlaRenderer

New `src/main/kotlin/ru/alepar/zx80/machine/BorderedUlaRenderer.kt`:

```kotlin
package ru.alepar.zx80.machine

import java.awt.Color
import java.awt.image.BufferedImage
import ru.alepar.zx80.cpu.Memory

/**
 * Wraps [UlaRenderer] and adds a 48-pixel Spectrum border around the 256x192 screen, producing
 * a 352x288 framebuffer. The border color comes from [BorderState] (read once per render call;
 * no mid-frame changes).
 */
class BorderedUlaRenderer(
    private val inner: UlaRenderer,
    private val border: BorderState,
) {
    fun render(mem: Memory, flashOn: Boolean = false): BufferedImage {
        val screen = inner.render(mem, flashOn)
        val out = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.color = Color(SpectrumPalette.color(border.read(), bright = false))
            g.fillRect(0, 0, WIDTH, HEIGHT)
            g.drawImage(screen, BORDER, BORDER, null)
        } finally {
            g.dispose()
        }
        return out
    }

    companion object {
        const val BORDER = 48
        const val WIDTH = 256 + BORDER * 2  // 352
        const val HEIGHT = 192 + BORDER * 2 // 288
    }
}
```

Tests (`BorderedUlaRendererTest`) — 5 assertions (helper: `pixel(img, x, y) = img.getRGB(x, y) and 0xFFFFFF`):

```kotlin
@Test fun `output dimensions are 352 by 288`() {
    val border = BorderState()
    val r = BorderedUlaRenderer(UlaRenderer(), border)
    val img = r.render(Memory(), flashOn = false)
    assertThat(img.width).isEqualTo(352)
    assertThat(img.height).isEqualTo(288)
}

@Test fun `border pixels match BorderState color`() {
    val border = BorderState().apply { write(2) } // red
    val r = BorderedUlaRenderer(UlaRenderer(), border)
    val img = r.render(Memory(), flashOn = false)
    assertThat(pixel(img, 0, 0)).isEqualTo(0xCD0000) // red non-bright
    assertThat(pixel(img, 351, 287)).isEqualTo(0xCD0000)
}

@Test fun `screen pixels at offset 48,48 match inner UlaRenderer output`() {
    val mem = Memory()
    mem.write(0x4000, 0xFF) // top-left cell pixel row, all pixels on
    mem.write(0x5800, 0x07) // ink=white, paper=black
    val r = BorderedUlaRenderer(UlaRenderer(), BorderState())
    val img = r.render(mem, flashOn = false)
    // (48, 48) is the top-left of the screen area; white because ink + bit=1.
    assertThat(pixel(img, 48, 48)).isEqualTo(0xCDCDCD)
    // (47, 48) is just inside the left border; black (border color 0).
    assertThat(pixel(img, 47, 48)).isEqualTo(0x000000)
}

@Test fun `changing BorderState between renders changes border color`() {
    val border = BorderState()
    val r = BorderedUlaRenderer(UlaRenderer(), border)
    border.write(0)
    val img0 = r.render(Memory(), flashOn = false)
    border.write(5) // cyan
    val img5 = r.render(Memory(), flashOn = false)
    assertThat(pixel(img0, 0, 0)).isEqualTo(0x000000)
    assertThat(pixel(img5, 0, 0)).isEqualTo(0x00CDCD)
}

@Test fun `bottom and right border pixels are border color`() {
    val border = BorderState().apply { write(4) } // green
    val r = BorderedUlaRenderer(UlaRenderer(), border)
    val img = r.render(Memory(), flashOn = false)
    // (256+48, anywhere in screen Y range) is in the right border.
    assertThat(pixel(img, 304, 100)).isEqualTo(0x00CD00)
    // (anywhere in screen X, 192+48) is in the bottom border.
    assertThat(pixel(img, 100, 240)).isEqualTo(0x00CD00)
}
```

### M2.8-C: SpectrumIoBus extension

Modify `src/main/kotlin/ru/alepar/zx80/machine/SpectrumIoBus.kt`. Add a
`borderState` constructor parameter; in `write`, forward the low 3 bits
to BorderState on ULA writes:

```kotlin
class SpectrumIoBus(
    private val keyboard: Keyboard,
    private val beeper: Beeper,
    private val border: BorderState,
) : IoBus {
    override fun read(port: Int): Int = /* unchanged */

    override fun write(port: Int, value: Int) {
        if ((port and 0x01) == 0) {
            beeper.onWrite((value ushr 4) and 1)
            border.write(value and 0x07)
        }
    }
}
```

Extend `SpectrumIoBusTest` — 2 new assertions:
- `write to ULA port with low 3 bits set updates BorderState`
- `write to ULA port with bit 4 set does not change BorderState from its current value` (verifies bit-4 stays beeper-only)

Update existing SpectrumIoBusTest constructor sites:
`SpectrumIoBus(Keyboard(), beeper)` → `SpectrumIoBus(Keyboard(), beeper, BorderState())`.

### M2.8-D: Pacer + SpectrumWindow + SpectrumCommand wiring

Modify `src/main/kotlin/ru/alepar/zx80/ui/Pacer.kt`:
- Change `renderer: UlaRenderer` ctor parameter type to
  `renderer: BorderedUlaRenderer`.
- `currentImage()` calls remain `renderer.render(machine.mem, flashOn())`.

Modify `src/main/kotlin/ru/alepar/zx80/ui/SpectrumWindow.kt`:
- Panel preferred size becomes
  `Dimension(BorderedUlaRenderer.WIDTH * scale, BorderedUlaRenderer.HEIGHT * scale)`
  i.e. 352 × scale × 288 × scale.

Modify `src/main/kotlin/ru/alepar/zx80/cli/SpectrumCommand.kt`:

```kotlin
override fun run() {
    val machine = Spectrum48k()
    val keyboard = Keyboard()
    val beeper = Beeper(machine.cpu)
    val border = BorderState()
    machine.cpu.io = SpectrumIoBus(keyboard, beeper, border)
    machine.reset()
    val audioOut = if (noAudio) NoOpAudioOutput else AudioOutput.tryOpen()
    val audioSink = BeeperAudioSink(beeper, audioOut)
    val renderer = BorderedUlaRenderer(UlaRenderer(), border)
    val pacer = Pacer(machine, renderer, audioSink = audioSink)
    val window = SpectrumWindow(pacer, keyboard, scale)
    window.show()
}
```

Update existing PacerTests to construct `BorderedUlaRenderer(UlaRenderer(), BorderState())`
where they currently pass `UlaRenderer()`.

### M2.8-E: Sweep + tag

Standard sweep + tag `m2-phase01-8` + push. Manual smoke is gated on
DISPLAY availability; document the outcome (likely "headless, skipped"
in the close reason).

### Out of scope

- Mid-frame border color changes (per-line tracking). YAGNI for M2.8.
- Bright bit on border (real Spectrum has no bright on border — only the
  3 color bits).
- Border-specific tests with real ROM (ROM never reaches border-set code
  while stuck at 0x11E6 per `zx80-1qc`).
- Border-only "demo" mode that lets the user cycle colors via CLI.

## Architecture

```
src/main/kotlin/ru/alepar/zx80/
  machine/BorderState.kt           NEW
  machine/BorderedUlaRenderer.kt   NEW
  machine/SpectrumIoBus.kt         MODIFY (ctor + write)
  ui/Pacer.kt                      MODIFY (renderer field type)
  ui/SpectrumWindow.kt             MODIFY (panel preferredSize)
  cli/SpectrumCommand.kt           MODIFY (wire BorderState)
src/test/kotlin/ru/alepar/zx80/
  machine/BorderStateTest.kt           NEW
  machine/BorderedUlaRendererTest.kt   NEW
  machine/SpectrumIoBusTest.kt         EXTEND (+ update existing ctor sites)
  ui/PacerTest.kt                      MODIFY (update ctor sites)
```

**Threading.** BorderState is a single `@Volatile var` written by the
Pacer thread (during runFrame's IoBus.write callback) and read by the
Pacer thread (in `BorderedUlaRenderer.render`, called from Pacer's
currentImage()). Both accesses are on the same thread today; volatile
is overcautious but cheap and future-proofs if EDT ever needs to peek
at border state directly.

**Color choice.** Border uses the non-bright palette via
`SpectrumPalette.color(idx, bright = false)`. Hardware doesn't support
bright on border.

## Test strategy

About 10 new assertions total across 2 new + 2 extended test files (the
BorderState and BorderedUlaRenderer tests above; the SpectrumIoBus and
Pacer extensions).

**Note on test names**: Kotlin disallows `:` in backtick-quoted test
method names (this has bitten M2.3, M2.5, and Phase H specs). Use
commas, dashes, or other separators throughout.

## Validation gates (M2.8-E)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. New tests pass (~10 assertions).
3. Existing tests still pass — all Pacer/SpectrumIoBus constructor cascades updated.
4. ZEXDOC: 0 IllegalStateException, 0 ERROR.
5. FUSE: 1354/1356.
6. Programs: 5/5.
7. Composite SCORE: >= 0.99 (Phase H preserved).
8. Headless CLI: `zx80 spectrum --help` exits 0.
9. Manual smoke (DISPLAY, deferred): `zx80 spectrum` opens a 704x576
   window (352x2 by 288x2). The 256x192 screen area is centered with a
   48-pixel black border. ROM doesn't write to the border while stuck at
   0x11E6, so the border stays black throughout.

Tag: `m2-phase01-8`.

## Work-unit breakdown

| WU | Description |
|---|---|
| M2.8-A | `BorderState` class + `BorderStateTest` (3 assertions). |
| M2.8-B | `BorderedUlaRenderer` class + `BorderedUlaRendererTest` (5 assertions). |
| M2.8-C | `SpectrumIoBus` extension (ctor + write); update existing SpectrumIoBusTest ctor sites; add 2 new assertions. |
| M2.8-D | Wiring: `Pacer` (renderer field type), `SpectrumWindow` (panel size), `SpectrumCommand` (construct BorderState + BorderedUlaRenderer + pass to Pacer/SpectrumIoBus). Update existing PacerTest ctor sites. |
| M2.8-E | Sweep + tag `m2-phase01-8` + push. |

Within-phase deps:
- M2.8-A independent.
- M2.8-B independent (composes UlaRenderer; doesn't need BorderState compiled to write code that *uses* BorderState type).
- Strictly: M2.8-B's code references `BorderState`, so M2.8-A must compile first. Treat as A → B.
- M2.8-C depends on A.
- M2.8-D depends on A + B + C.
- M2.8-E depends on D.

Linear order recommended: A → B → C → D → E.

## Risks

- **Constructor signature cascades.** SpectrumIoBus and Pacer ctor changes
  break every test/caller. Mostly mechanical — update existing tests to
  pass `BorderState()` placeholders and a `BorderedUlaRenderer` over a
  bare `UlaRenderer`. Affects: SpectrumIoBusTest (all assertions),
  PacerTest (all setup helpers), SpectrumCommand.
- **Color/palette mapping.** Border idx 0 is black (0x000000); idx 2 is
  red (0xCD0000); idx 5 is cyan (0x00CDCD); idx 4 is green (0x00CD00).
  Tests pin these exact values via SpectrumPalette. If palette values
  change in the future, tests break loudly.
- **BufferedImage.createGraphics in tests.** Headless-safe (BufferedImage
  is heap-only).
- **TEST NAME COLONS** — recurring author trap. Use commas/dashes in all
  backtick-quoted Kotlin test names. (Specs M2.3, M2.5, and Phase H all
  tripped on this. Don't repeat.)
- **SpectrumPalette.color(2, false)** returns `0xCD0000` per the palette
  table. Verified during M2.3 design. Tests assume this value.
