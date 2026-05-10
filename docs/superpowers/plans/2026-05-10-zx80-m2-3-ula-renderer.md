# M2.3 Headless ULA Video Renderer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Headless `UlaRenderer` that reads Spectrum screen RAM (0x4000-0x5AFF) and produces a 256×192 `java.awt.image.BufferedImage` (TYPE_INT_RGB). Pixel-accurate against the canonical Spectrum palette; flash phase caller-controlled.

**Architecture:** Two new files in `machine/`: a stateless `SpectrumPalette` object with the 16-entry color table, and a `UlaRenderer` class with `render(mem: Memory, flashOn: Boolean): BufferedImage`. No modifications to existing code; the renderer is pure read-from-Memory.

**Tech Stack:** Kotlin 2.x, Gradle Kotlin DSL, JUnit Jupiter 5, AssertJ, `java.awt.image.BufferedImage` (JDK 21).

**Spec:** `docs/superpowers/specs/2026-05-10-zx80-m2-3-ula-renderer-design.md`

**Within-phase deps:** Task 1 → Task 2 (Task 2 references `SpectrumPalette`). Task 3 depends on Task 2. Task 4 depends on all.

---

## Task 1: SpectrumPalette (M2.3-A)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/machine/SpectrumPalette.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/machine/SpectrumPaletteTest.kt`

### Step 1.1: Write the failing test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/machine/SpectrumPaletteTest.kt`:

```kotlin
package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpectrumPaletteTest {
    @Test
    fun `black is 0x000000 in both phases`() {
        assertThat(SpectrumPalette.color(0, bright = false)).isEqualTo(0x000000)
        assertThat(SpectrumPalette.color(0, bright = true)).isEqualTo(0x000000)
    }

    @Test
    fun `white non-bright is 0xCDCDCD`() {
        assertThat(SpectrumPalette.color(7, bright = false)).isEqualTo(0xCDCDCD)
    }

    @Test
    fun `white bright is 0xFFFFFF`() {
        assertThat(SpectrumPalette.color(7, bright = true)).isEqualTo(0xFFFFFF)
    }

    @Test
    fun `red non-bright is 0xCD0000`() {
        assertThat(SpectrumPalette.color(2, bright = false)).isEqualTo(0xCD0000)
    }

    @Test
    fun `cyan bright is 0x00FFFF`() {
        assertThat(SpectrumPalette.color(5, bright = true)).isEqualTo(0x00FFFF)
    }
}
```

### Step 1.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.SpectrumPaletteTest"
```

Expected: compilation failure (`Unresolved reference: SpectrumPalette`).

### Step 1.3: Implement SpectrumPalette

- [ ] Create `src/main/kotlin/ru/alepar/zx80/machine/SpectrumPalette.kt`:

```kotlin
package ru.alepar.zx80.machine

/**
 * Canonical ZX Spectrum 48K palette: 8 colors × 2 brightness levels.
 *
 * Per Sean Young's TUZD and Wikipedia "ZX Spectrum graphic modes": non-bright values use 0xCD
 * on active channels; bright values use 0xFF. Black is 0x000000 in both phases.
 *
 * Returned as packed RGB ints (alpha bits = 0) suitable for `BufferedImage.setRGB` with
 * TYPE_INT_RGB images.
 *
 * Spectrum color encoding: bit 0 = blue, bit 1 = red, bit 2 = green (so 7 = white).
 */
object SpectrumPalette {

    private const val L = 0xCD // non-bright "low" intensity
    private const val H = 0xFF // bright "high" intensity

    private val table =
        intArrayOf(
            rgb(0, 0, 0), // 0  black
            rgb(0, 0, L), // 1  blue
            rgb(L, 0, 0), // 2  red
            rgb(L, 0, L), // 3  magenta
            rgb(0, L, 0), // 4  green
            rgb(0, L, L), // 5  cyan
            rgb(L, L, 0), // 6  yellow
            rgb(L, L, L), // 7  white (non-bright)
            rgb(0, 0, 0), // 8  black bright (still black)
            rgb(0, 0, H), // 9  blue bright
            rgb(H, 0, 0), // 10 red bright
            rgb(H, 0, H), // 11 magenta bright
            rgb(0, H, 0), // 12 green bright
            rgb(0, H, H), // 13 cyan bright
            rgb(H, H, 0), // 14 yellow bright
            rgb(H, H, H), // 15 white bright
        )

    /** Look up a packed RGB color for a Spectrum 3-bit color index. */
    fun color(index: Int, bright: Boolean): Int {
        val i = (index and 0x07) or (if (bright) 0x08 else 0)
        return table[i]
    }

    private fun rgb(r: Int, g: Int, b: Int): Int =
        ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
```

### Step 1.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.SpectrumPaletteTest"
```

Expected: 5 tests, all PASS.

### Step 1.5: Run the full test suite

- [ ] Run:

```bash
./gradlew test
```

Expected: all tests pass. New file is additive; no existing tests touched.

### Step 1.6: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/machine/SpectrumPalette.kt \
        src/test/kotlin/ru/alepar/zx80/machine/SpectrumPaletteTest.kt
git commit -m "$(cat <<'EOF'
feat(machine): SpectrumPalette — 16-entry color table

8 Spectrum colors x 2 brightness levels. Non-bright at 0xCD, bright
at 0xFF, black 0x000000 in both phases (per Sean Young TUZD and
Wikipedia). Returned as packed RGB ints for BufferedImage.setRGB.
Used by UlaRenderer in the next commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: UlaRenderer (M2.3-B)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/machine/UlaRenderer.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/machine/UlaRendererTest.kt`

### Step 2.1: Write the failing test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/machine/UlaRendererTest.kt`:

```kotlin
package ru.alepar.zx80.machine

import java.awt.image.BufferedImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Memory

class UlaRendererTest {

    private val renderer = UlaRenderer()

    /** BufferedImage.TYPE_INT_RGB returns getRGB() with alpha=0xFF; mask it for comparison. */
    private fun pixel(img: BufferedImage, x: Int, y: Int): Int = img.getRGB(x, y) and 0xFFFFFF

    @Test
    fun `all-zero screen renders all-black`() {
        val mem = Memory()
        val img = renderer.render(mem, flashOn = false)
        for (y in 0 until 192) {
            for (x in 0 until 256) {
                assertThat(pixel(img, x, y)).`as`("x=%d y=%d", x, y).isEqualTo(0x000000)
            }
        }
    }

    @Test
    fun `one cell top-left white ink black paper`() {
        val mem = Memory()
        mem.write(0x4000, 0xFF) // all 8 pixels on
        mem.write(0x5800, 0x07) // ink=white(7), paper=black(0)
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 0)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
        // Pixel just past the cell should still be black.
        assertThat(pixel(img, 8, 0)).isEqualTo(0x000000)
        assertThat(pixel(img, 0, 1)).isEqualTo(0x000000)
    }

    @Test
    fun `row-interleaving y=8 maps to address 0x4020`() {
        val mem = Memory()
        mem.write(0x4020, 0xFF) // pixel byte for screen row 8, x-byte 0
        mem.write(0x5800 + 32, 0x07) // attribute row 1 (y=8..15), col 0
        val img = renderer.render(mem, flashOn = false)
        // Row 8 cols 0..7 should be white.
        for (x in 0..7) {
            assertThat(pixel(img, x, 8)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
        // Rows 0..7 stay black.
        for (y in 0..7) {
            assertThat(pixel(img, 0, y)).`as`("y=%d", y).isEqualTo(0x000000)
        }
        // Row 9 stays black.
        assertThat(pixel(img, 0, 9)).isEqualTo(0x000000)
    }

    @Test
    fun `row-interleaving y=64 maps to address 0x4800 (second screen bank)`() {
        val mem = Memory()
        mem.write(0x4800, 0xFF) // pixel byte for screen row 64
        mem.write(0x5800 + 8 * 32, 0x07) // attribute row 8 (y=64..71)
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 64)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
        // Row 63 and 65 stay black.
        assertThat(pixel(img, 0, 63)).isEqualTo(0x000000)
        assertThat(pixel(img, 0, 65)).isEqualTo(0x000000)
    }

    @Test
    fun `row-interleaving y=128 maps to address 0x5000 (third screen bank)`() {
        val mem = Memory()
        mem.write(0x5000, 0xFF)
        mem.write(0x5800 + 16 * 32, 0x07) // attribute row 16 (y=128..135)
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 128)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
        assertThat(pixel(img, 0, 127)).isEqualTo(0x000000)
        assertThat(pixel(img, 0, 129)).isEqualTo(0x000000)
    }

    @Test
    fun `flash off, bit 7 of attr is ignored when flashOn=false`() {
        val mem = Memory()
        mem.write(0x4000, 0xFF)
        mem.write(0x5800, 0x87) // FLASH=1, ink=white, paper=black
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 0)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
    }

    @Test
    fun `flash on, bit 7 of attr inverts ink and paper when flashOn=true`() {
        val mem = Memory()
        mem.write(0x4000, 0xFF)
        mem.write(0x5800, 0x87) // FLASH=1, ink=white, paper=black
        val img = renderer.render(mem, flashOn = true)
        // Inverted: pixel bit=1 now selects paper (black).
        for (x in 0..7) {
            assertThat(pixel(img, x, 0)).`as`("x=%d", x).isEqualTo(0x000000)
        }
    }

    @Test
    fun `bright bit, attribute 0x47 renders 0xFFFFFF not 0xCDCDCD`() {
        val mem = Memory()
        mem.write(0x4000, 0xFF)
        mem.write(0x5800, 0x47) // BRIGHT=1, ink=white, paper=black
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 0)).`as`("x=%d", x).isEqualTo(0xFFFFFF)
        }
    }

    @Test
    fun `pixel bit zero selects paper not ink`() {
        val mem = Memory()
        mem.write(0x4000, 0x00) // all pixel bits off → all paper
        mem.write(0x5800, 0x38) // paper=white(7), ink=black(0), BRIGHT=0, FLASH=0
        val img = renderer.render(mem, flashOn = false)
        for (x in 0..7) {
            assertThat(pixel(img, x, 0)).`as`("x=%d", x).isEqualTo(0xCDCDCD)
        }
    }
}
```

### Step 2.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.UlaRendererTest"
```

Expected: compilation failure (`Unresolved reference: UlaRenderer`).

### Step 2.3: Implement UlaRenderer

- [ ] Create `src/main/kotlin/ru/alepar/zx80/machine/UlaRenderer.kt`:

```kotlin
package ru.alepar.zx80.machine

import java.awt.image.BufferedImage
import ru.alepar.zx80.cpu.Memory

/**
 * Reads ZX Spectrum 48K screen RAM (0x4000-0x5AFF) and produces a 256x192 BufferedImage.
 * Headless; no host window. M2.4 wraps the output in a display; M2.8 will add a border.
 *
 * `flashOn` selects the flash phase: false = ink and paper as written; true = ink and paper
 * swapped for cells with attribute bit 7 set. The caller (M2.4) toggles this every 16 frames.
 *
 * Pixel-data layout (0x4000-0x57FF, 6144 bytes): rows are interleaved into three banks of 64
 * rows each. Address for screen row y:
 *
 *     0x4000 | ((y and 0xC0) shl 5) | ((y and 0x07) shl 8) | ((y and 0x38) shl 2)
 *
 * Attributes (0x5800-0x5AFF, 768 bytes): one byte per 8x8 cell, laid out linearly.
 * Byte format:
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
            val rowBase =
                0x4000 or
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

### Step 2.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.UlaRendererTest"
```

Expected: 9 tests, all PASS.

If a row-interleaving test fails (test 3, 4, or 5), the formula in `UlaRenderer.render` is wrong. Re-check against the spec; the canonical form is `0x4000 | ((y & 0xC0) << 5) | ((y & 0x07) << 8) | ((y & 0x38) << 2)`. Sources: Sean Young TUZD §14, Wikipedia ZX Spectrum graphic modes.

### Step 2.5: Run the full test suite

- [ ] Run:

```bash
./gradlew test
```

Expected: all tests pass — existing M1 + M2.1 + M2.2 + new SpectrumPalette + UlaRenderer.

### Step 2.6: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/machine/UlaRenderer.kt \
        src/test/kotlin/ru/alepar/zx80/machine/UlaRendererTest.kt
git commit -m "$(cat <<'EOF'
feat(machine): UlaRenderer — headless 256x192 BufferedImage from screen RAM

Reads Spectrum screen RAM (0x4000-0x5AFF) and produces a 256x192 image
using the canonical row-interleaving formula and the Spectrum palette.
flashOn is caller-controlled; M2.4 will toggle it every 16 frames.
Pure read-from-Memory; no CPU dependency.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Real-ROM smoke gate (M2.3-C)

**Files:** none committed (this task adds a temporary test, runs it, then reverts).

### Step 3.1: Add a throwaway smoke test

- [ ] Open `src/test/kotlin/ru/alepar/zx80/machine/UlaRendererTest.kt`. Append a new test method just before the closing `}`:

```kotlin
    @Test
    fun `SMOKE render the live machine after reset returns 256x192 image`() {
        val machine = Spectrum48k()
        machine.reset()
        val img = renderer.render(machine.mem, flashOn = false)
        assertThat(img.width).isEqualTo(256)
        assertThat(img.height).isEqualTo(192)
        // Pixel data is meaningless at boot (screen RAM is zero-initialized RAM), but the
        // render call itself must succeed without throwing.
    }
```

### Step 3.2: Run the smoke test

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.UlaRendererTest.SMOKE render the live machine after reset returns 256x192 image"
```

Expected: PASS. If it fails or throws, that means `Spectrum48k().reset()` itself crashes (M2.1 regression) or there's an out-of-bounds read in `UlaRenderer` that the synthetic tests didn't catch — STOP, do not proceed to Task 4. Investigate and fix.

### Step 3.3: Revert the throwaway test

The smoke test is intentionally not committed; M2.9 sweep can introduce a hardened version.

- [ ] Run:

```bash
git diff src/test/kotlin/ru/alepar/zx80/machine/UlaRendererTest.kt | head -20
```

Verify that only the SMOKE test was added (no other changes).

- [ ] Revert:

```bash
git checkout src/test/kotlin/ru/alepar/zx80/machine/UlaRendererTest.kt
```

- [ ] Confirm the file is back to the committed state:

```bash
git status
```

Expected: `nothing to commit, working tree clean`.

---

## Task 4: Sweep + tag (M2.3-D)

**Files:** none (validation + git tag + push).

### Step 4.1: Run the full check

- [ ] Run:

```bash
./gradlew clean check installDist
```

Expected: BUILD SUCCESSFUL.

### Step 4.2: Run the score harness for regression check

- [ ] Run:

```bash
./build/install/zx80/bin/zx80 score
```

Expected:
- programs: 5/5 PASS
- fuse: 1354/1356 PASS
- ZEXDOC: 0 IllegalStateException, 0 ERROR
- composite SCORE: ≥ 0.966

If FUSE drops below 1354 or programs drops below 5, STOP. The renderer adds files and doesn't modify behavior, so any regression is mysterious — investigate before tagging.

### Step 4.3: Tag the milestone

- [ ] Apply the tag:

```bash
git tag -a m2-phase01-3 -m "M2.3: Headless ULA video renderer (256x192 BufferedImage)"
git tag --list | grep m2-phase01-3
```

Expected: `m2-phase01-3` listed.

### Step 4.4: Close the beads issue and push

- [ ] Claim and close M2.3 in beads, then push:

```bash
bd update zx80-mup --claim
bd close zx80-mup --reason="M2.3 complete: SpectrumPalette + UlaRenderer (256x192 BufferedImage from screen RAM). 15 new test assertions covering palette, row-interleaving across 3 banks, attribute decoding (ink/paper/bright/flash). Tag m2-phase01-3 applied. SCORE preserved at >=0.966."
git pull --rebase
bd dolt push
git push
git push --tags
git status
```

Expected: `git status` shows `On branch opus-4.7` and `up to date with 'origin/opus-4.7'`. Tag pushed to remote.

---

## Self-review notes (recorded after writing the plan)

**Spec coverage check:**

| Spec section | Task |
|---|---|
| M2.3-A SpectrumPalette | Task 1 |
| M2.3-B UlaRenderer | Task 2 |
| M2.3-C Manual smoke | Task 3 |
| M2.3-D Sweep + tag | Task 4 |
| Validation gates 1-8 | Task 4 (steps 4.1-4.4) plus Task 3 step 3.2 (gate 8) |

**No-placeholder check** — every step contains executable code or commands. The throwaway smoke test in Task 3 is intentional (a gate, not a permanent test) and the plan explicitly reverts it.

**Type/name consistency** — `SpectrumPalette.color(index, bright)`, `UlaRenderer.render(mem, flashOn)`, `UlaRenderer.WIDTH`/`HEIGHT` companion constants, attribute decoding (bits 0-2 ink, 3-5 paper, 6 BRIGHT, 7 FLASH), pixel-bit=1 selects ink — all used identically across spec, plan, and tests.
