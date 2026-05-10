# M2.3: Headless ULA Video Renderer — Design

## Goal

A headless `UlaRenderer` that reads the Spectrum's screen RAM
(0x4000-0x5AFF) and produces a 256×192 `java.awt.image.BufferedImage`
(TYPE_INT_RGB). Pixel-accurate against the canonical Spectrum palette.
No border, no host window, no flash auto-toggle — caller passes
`flashOn: Boolean`. Score harness untouched.

## Context

Beads issue: `zx80-mup` (parent: `zx80-48f` M2 epic).

After M2.2, the machine boots the real ROM and the frame scheduler
fires the 50Hz interrupt, but there's no way to *see* what the ROM is
drawing. M2.3 produces the pixel data; M2.4 wraps it in a host window;
M2.8 adds the border state. The three are deliberately split so each
WU has a focused, testable surface.

The renderer is pure read-from-memory: take a `Memory` and a flash
phase, return a `BufferedImage`. No CPU dependency, no IoBus. This
keeps it trivially unit-testable with synthetic memory bytes and
decouples it from the broader machine state.

## Scope

### M2.3-A: SpectrumPalette

New `machine/SpectrumPalette.kt`:

```kotlin
package ru.alepar.zx80.machine

/**
 * Canonical ZX Spectrum 48K palette: 8 colors × 2 brightness levels.
 *
 * Per Sean Young's TUZD and Wikipedia "ZX Spectrum graphic modes":
 * non-bright values use 0xCD on active channels; bright values use 0xFF.
 * Black is 0x000000 in both phases.
 *
 * Returned as packed RGB ints (alpha bits = 0) suitable for
 * `BufferedImage.setRGB`.
 */
object SpectrumPalette {

    private const val L = 0xCD  // non-bright "low" intensity
    private const val H = 0xFF  // bright "high" intensity

    // Indices 0..7 are normal; 8..15 are bright. Spectrum encoding:
    //   bit 0 = blue, bit 1 = red, bit 2 = green (so 7 = white).
    private val table = intArrayOf(
        rgb(0, 0, 0),   // 0 black
        rgb(0, 0, L),   // 1 blue
        rgb(L, 0, 0),   // 2 red
        rgb(L, 0, L),   // 3 magenta
        rgb(0, L, 0),   // 4 green
        rgb(0, L, L),   // 5 cyan
        rgb(L, L, 0),   // 6 yellow
        rgb(L, L, L),   // 7 white (non-bright)
        rgb(0, 0, 0),   // 8 black bright (still black)
        rgb(0, 0, H),   // 9 blue bright
        rgb(H, 0, 0),   // 10 red bright
        rgb(H, 0, H),   // 11 magenta bright
        rgb(0, H, 0),   // 12 green bright
        rgb(0, H, H),   // 13 cyan bright
        rgb(H, H, 0),   // 14 yellow bright
        rgb(H, H, H),   // 15 white bright
    )

    fun color(index: Int, bright: Boolean): Int {
        val i = (index and 0x07) or (if (bright) 0x08 else 0)
        return table[i]
    }

    private fun rgb(r: Int, g: Int, b: Int): Int =
        ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
```

### M2.3-B: UlaRenderer

New `machine/UlaRenderer.kt`:

```kotlin
package ru.alepar.zx80.machine

import java.awt.image.BufferedImage
import ru.alepar.zx80.cpu.Memory

/**
 * Reads ZX Spectrum 48K screen RAM (0x4000-0x5AFF) and produces a 256×192
 * BufferedImage. Headless; no host window. M2.4 wraps the output in a
 * display; M2.8 will add a border around it.
 *
 * `flashOn` selects which half of the flash cycle to render: false = ink
 * and paper as written; true = ink and paper swapped for cells with
 * attribute bit 7 set. The caller (M2.4) toggles this every 16 frames
 * for a 50/16 ≈ 3 Hz flash rate.
 *
 * Pixel-data layout (0x4000-0x57FF, 6144 bytes): rows are interleaved
 * into three banks of 64 rows each; within a bank, rows are interleaved
 * across 8 lines per "char row". Address for screen row y:
 *
 *     0x4000 | ((y and 0xC0) shl 5) | ((y and 0x07) shl 8) | ((y and 0x38) shl 2)
 *
 * Attributes (0x5800-0x5AFF, 768 bytes): one byte per 8×8 cell, laid out
 * linearly in 32×24 cell grid. Byte format:
 *
 *   bit 7    : FLASH
 *   bit 6    : BRIGHT
 *   bits 3-5 : PAPER color (0..7)
 *   bits 0-2 : INK color (0..7)
 *
 * A pixel bit of 1 selects INK; 0 selects PAPER.
 */
class UlaRenderer {

    fun render(mem: Memory, flashOn: Boolean = false): BufferedImage {
        val img = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until HEIGHT) {
            val rowBase = 0x4000 or
                ((y and 0xC0) shl 5) or
                ((y and 0x07) shl 8) or
                ((y and 0x38) shl 2)
            val attrBase = 0x5800 + (y ushr 3) * 32
            for (xByte in 0 until 32) {
                val pixels = mem.read(rowBase + xByte)
                val attr = mem.read(attrBase + xByte)
                val bright = (attr and 0x40) != 0
                val ink = SpectrumPalette.color(attr and 0x07, bright)
                val paper = SpectrumPalette.color((attr ushr 3) and 0x07, bright)
                val flash = (attr and 0x80) != 0
                val fg = if (flash && flashOn) paper else ink
                val bg = if (flash && flashOn) ink else paper
                val pxBase = xByte * 8
                for (bit in 0..7) {
                    val on = (pixels shr (7 - bit)) and 1
                    img.setRGB(pxBase + bit, y, if (on == 1) fg else bg)
                }
            }
        }
        return img
    }

    companion object {
        const val WIDTH = 256
        const val HEIGHT = 192
    }
}
```

### M2.3-C: Manual smoke

Not a JUnit test — just a sanity check during the sweep:
`Spectrum48k().reset(); UlaRenderer().render(machine.mem, false)` returns
a non-null 256×192 BufferedImage without throwing. The rendered content
won't be meaningful (ROM hasn't yet drawn anything into screen RAM — at
boot the screen-RAM area is zeroed RAM), but it confirms the renderer
doesn't crash on the live machine.

### M2.3-D: Sweep + tag

Standard sweep: clean check, regression suites, tag `m2-phase01-3`.

### Out of scope

- Border rendering (M2.8 will add OUT(0xFE) low 3 bits → border color).
- Host window / Swing/AWT display backend (M2.4).
- Flash auto-toggle frame counter (M2.4 will track and pass
  `flashOn` based on its own frame timer).
- Reference-screenshot PNG comparison (M2.9 sweep can add this).
- Memory contention affecting renders mid-frame (M2.7 may rework
  the renderer to be per-line if contention demands cycle-accuracy).
- Palette tweaking / brightness curves / NTSC vs PAL phosphor sim.

## Architecture

**File layout (new files only — no modifications to existing code):**

```
src/main/kotlin/ru/alepar/zx80/machine/
  SpectrumPalette.kt    NEW
  UlaRenderer.kt        NEW
src/test/kotlin/ru/alepar/zx80/machine/
  SpectrumPaletteTest.kt   NEW
  UlaRendererTest.kt       NEW
```

**Why a class (not an object) for `UlaRenderer`.** Currently stateless;
could be a top-level function or `object`. We pick `class` to align with
M2.7 contention (which will likely introduce per-frame state — e.g., a
buffer reused across calls) and to mirror `FrameScheduler` (also a
class for a single-concern logical unit). One instance per machine is
fine.

**Why `Memory` not `Spectrum48k`.** The renderer only reads bytes; it
has no need for CPU state, IoBus, or scheduler. Tests can construct a
bare `Memory()` (no write-guard) and load synthetic screen bytes
without booting the full machine. M2.4 wraps it as `UlaRenderer()
.render(machine.mem, flashOn)`.

**Stateless safety.** `UlaRenderer.render()` is pure (function of
`Memory` snapshot + `flashOn`). Two consecutive calls with the same
inputs return identical pixel data. No mutation of `Memory`. The
returned `BufferedImage` is owned by the caller.

## Test strategy

### SpectrumPaletteTest — 6 assertions

- Black is `0x000000` in both phases:
  - `SpectrumPalette.color(0, bright=false) == 0x000000`
  - `SpectrumPalette.color(0, bright=true) == 0x000000`
- White non-bright is `0xCDCDCD`:
  - `SpectrumPalette.color(7, bright=false) == 0xCDCDCD`
- White bright is `0xFFFFFF`:
  - `SpectrumPalette.color(7, bright=true) == 0xFFFFFF`
- Red non-bright (idx 2):
  - `SpectrumPalette.color(2, bright=false) == 0xCD0000`
- Cyan bright (idx 5):
  - `SpectrumPalette.color(5, bright=true) == 0x00FFFF`

### UlaRendererTest — 9 assertions

Tests use bare `Memory()` (no write-guard) and write synthetic screen
bytes directly with `mem.write(addr, value)`. Image pixel comparison
must mask to `0xFFFFFF` because `BufferedImage.TYPE_INT_RGB` returns
`getRGB(x, y)` with alpha=0xFF set in the high byte:

```kotlin
private fun pixel(img: BufferedImage, x: Int, y: Int): Int =
    img.getRGB(x, y) and 0xFFFFFF
```

Test cases:

1. **All-zero screen renders all-black.** Fresh `Memory()`; render →
   every pixel is `0x000000`. Asserts via a loop over all 256×192
   pixels (one assertion in a forEach).
2. **One cell top-left, white ink black paper.** `mem.write(0x4000,
   0xFF); mem.write(0x5800, 0x07)` → row 0 cols 0..7 are `0xCDCDCD`,
   all other pixels in the image are `0x000000`.
3. **Row-interleaving y=8 maps to address 0x4020.**
   `mem.write(0x4020, 0xFF); mem.write(0x5800 + 32, 0x07)` (attr row 1)
   → row 8 cols 0..7 white, rows 0..7 black, rows 9..191 black.
4. **Row-interleaving y=64 maps to address 0x4800** (second bank).
   `mem.write(0x4800, 0xFF); mem.write(0x5800 + 8*32, 0x07)` →
   row 64 cols 0..7 white; rows 0..63 black; rows 65..191 black.
5. **Row-interleaving y=128 maps to address 0x5000** (third bank).
   `mem.write(0x5000, 0xFF); mem.write(0x5800 + 16*32, 0x07)` →
   row 128 cols 0..7 white.
6. **Flash off: bit 7 of attr ignored when flashOn=false.**
   `mem.write(0x4000, 0xFF); mem.write(0x5800, 0x87)` (FLASH + white
   ink, black paper) → `render(flashOn=false)` row 0 cols 0..7 white.
7. **Flash on: bit 7 inverts ink/paper when flashOn=true.** Same
   memory state as test 6, `render(flashOn=true)` → row 0 cols 0..7
   black (ink and paper swapped).
8. **Bright bit: attribute 0x47 renders 0xFFFFFF not 0xCDCDCD.**
   `mem.write(0x4000, 0xFF); mem.write(0x5800, 0x47)` (BRIGHT + white
   ink, black paper) → row 0 cols 0..7 exactly `0xFFFFFF`.
9. **Paper bits select non-pixel cells.** `mem.write(0x4000, 0x00)`
   (all pixel bits off → all paper), `mem.write(0x5800, 0x38)`
   (paper=white, ink=black) → row 0 cols 0..7 white. Verifies that
   pixel-bit=0 selects paper, not ink.

About 15 assertions total. Tight, fast, no real-ROM dependency.

## Validation gates (WU M2.3-D)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. All new tests pass (~15 assertions across 2 new test files).
3. Existing M1 + M2.1 + M2.2 tests still pass. The renderer adds new
   files and modifies nothing.
4. ZEXDOC: 0 IllegalStateException, 0 ERROR.
5. FUSE: 1354/1356 (regression check is trivial — no behavior changes).
6. Programs: 5/5.
7. Composite SCORE: ≥ 0.966 (no regression; rendering not in score formula).
8. **Manual smoke (not a JUnit gate):** add a temporary throwaway test
   (similar pattern to M2.2's FRAMES stretch gate) that does:
   ```kotlin
   val machine = Spectrum48k()
   machine.reset()
   val img = UlaRenderer().render(machine.mem, false)
   assertThat(img.width).isEqualTo(256)
   assertThat(img.height).isEqualTo(192)
   ```
   Run it once, confirm PASS, then `git checkout` to revert. Confirms
   the renderer doesn't throw on the live machine state.

Tag: `m2-phase01-3`.

## Work-unit breakdown

| WU | Description |
|---|---|
| M2.3-A | `SpectrumPalette` object + `SpectrumPaletteTest` (6 assertions). |
| M2.3-B | `UlaRenderer` class + `UlaRendererTest` (9 assertions covering pixel mapping, row-interleaving across 3 banks, attribute layout, flash, bright, paper-selection). |
| M2.3-C | Manual real-ROM smoke verification (throwaway test pattern; gate 8). |
| M2.3-D | Sweep + tag `m2-phase01-3`. |

Within-phase deps: A → B (B references SpectrumPalette). C+D depend on
A+B. Linear order recommended.

## Risks

- **Row-interleaving formula off-by-one.** Most error-prone part of any
  ZX renderer. Formula `0x4000 | ((y and 0xC0) shl 5) | ((y and 0x07)
  shl 8) | ((y and 0x38) shl 2)` is canonical (Sean Young's TUZD §14;
  Wikipedia ZX Spectrum graphic modes). Tests 3, 4, 5 cover bank
  boundaries (y=8, 64, 128); a typo will fail at least one.
- **Palette value disagreement.** Different references give 0xC0, 0xCD,
  or 0xD7 for "non-bright max." We pin to **0xCD** (Sean Young's TUZD;
  Wikipedia ZX Spectrum article cites ~80% intensity, 0xCD ≈ 80% of
  0xFF). If a future cross-reference test against Fuse screenshots
  surfaces mismatch, we re-pin in a follow-up.
- **BufferedImage.setRGB / getRGB alpha handling.** TYPE_INT_RGB stores
  ARGB ints with alpha=0xFF *implicit*; `setRGB` stores the low 24 bits
  as RGB and reads back with alpha=0xFF in the high byte. Tests assert
  against `getRGB(x, y) and 0xFFFFFF` to ignore alpha. Forgetting this
  produces "expected 0xCDCDCD but was 0xFFCDCDCD" failures.
- **Ink vs paper bit interpretation.** Attribute byte: bits 0-2 = ink,
  bits 3-5 = paper, bit 6 = BRIGHT, bit 7 = FLASH. A pixel BIT=1
  selects ink; BIT=0 selects paper. Easy to invert. Test 9 covers it.
- **TYPE_INT_RGB vs TYPE_INT_ARGB.** We use TYPE_INT_RGB (no alpha
  channel). If M2.4 needs alpha (unlikely — Spectrum is fully opaque),
  switch then.
