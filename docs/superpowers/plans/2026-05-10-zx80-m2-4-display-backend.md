# M2.4 Display Backend (Swing Host Window) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Host-window display backend that drives Spectrum48k.runFrame() at real-time 50Hz, tracks flash phase, paints the framebuffer in a 2×-scaled Swing window, and is launched via `zx80 spectrum`.

**Architecture:** New `ui/` package with three classes — `Clock` (interface + RealClock using LockSupport.parkNanos), `Pacer` (headless 50Hz logic), `SpectrumWindow` (Swing JFrame + daemon worker thread). New `cli/SpectrumCommand.kt` registered in `Zx80Cli`. Pacer is testable via a FakeClock; SpectrumWindow is verified manually.

**Tech Stack:** Kotlin 2.x, Gradle Kotlin DSL, JUnit Jupiter 5, AssertJ, Clikt (CLI), Swing/AWT (JDK 21).

**Spec:** `docs/superpowers/specs/2026-05-10-zx80-m2-4-display-backend-design.md`

**Within-phase deps:** Task 1 → Task 2 (Pacer uses Clock). Task 3 depends on Task 2 (Window uses Pacer). Task 4 depends on Task 3 (CLI instantiates Window). Task 5 depends on all.

---

## Task 1: Clock + RealClock (M2.4-A)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/ui/Clock.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/ui/ClockTest.kt`

### Step 1.1: Write the failing test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/ui/ClockTest.kt`:

```kotlin
package ru.alepar.zx80.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClockTest {
    @Test
    fun `RealClock nowNanos is monotonically non-decreasing`() {
        val a = RealClock.nowNanos()
        val b = RealClock.nowNanos()
        assertThat(b).isGreaterThanOrEqualTo(a)
    }

    @Test
    fun `RealClock parkUntilNanos returns within a reasonable upper bound`() {
        val start = RealClock.nowNanos()
        RealClock.parkUntilNanos(start + 1_000_000) // 1ms target
        val elapsed = RealClock.nowNanos() - start
        // Loose upper bound to survive CI load; precision isn't the test, just that it returns.
        assertThat(elapsed).isLessThan(50_000_000L) // 50ms
    }
}
```

### Step 1.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.ui.ClockTest"
```

Expected: compilation failure (`Unresolved reference: RealClock`).

### Step 1.3: Implement Clock + RealClock

- [ ] Create `src/main/kotlin/ru/alepar/zx80/ui/Clock.kt`:

```kotlin
package ru.alepar.zx80.ui

import java.util.concurrent.locks.LockSupport

/**
 * Wall-clock + park abstraction. Production uses [RealClock]; tests inject a fake clock to
 * verify drift-free pacing without sleeping. `LockSupport.parkNanos` is used over
 * `Thread.sleep` because the latter rounds up to 1ms before calling the VM and discards sub-ms
 * precision; on Linux with hrtimers, parkNanos gives ~50us accuracy.
 */
interface Clock {
    fun nowNanos(): Long

    fun parkUntilNanos(target: Long)
}

object RealClock : Clock {
    override fun nowNanos(): Long = System.nanoTime()

    override fun parkUntilNanos(target: Long) {
        val now = nowNanos()
        if (target > now) LockSupport.parkNanos(target - now)
    }
}
```

### Step 1.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.ui.ClockTest"
```

Expected: 2 tests, all PASS.

### Step 1.5: Run the full test suite

- [ ] Run:

```bash
./gradlew test
```

Expected: all tests pass. New package, no modifications elsewhere.

### Step 1.6: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/ui/Clock.kt \
        src/test/kotlin/ru/alepar/zx80/ui/ClockTest.kt
git commit -m "$(cat <<'EOF'
feat(ui): Clock abstraction + RealClock using LockSupport.parkNanos

Wall-clock plus park interface so Pacer (next commit) can be tested
with a FakeClock. RealClock uses System.nanoTime and parkNanos rather
than Thread.sleep, which rounds up to 1ms and discards sub-ms
precision (matters for 20ms-frame timing).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Pacer + FakeClock + PacerTest (M2.4-B)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/ui/Pacer.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/ui/FakeClock.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/ui/PacerTest.kt`

### Step 2.1: Write the FakeClock test helper

- [ ] Create `src/test/kotlin/ru/alepar/zx80/ui/FakeClock.kt`:

```kotlin
package ru.alepar.zx80.ui

/**
 * Test helper. Snap-to-target Clock — parkUntilNanos jumps `now` straight to the target.
 * Records every park target so tests can assert the cadence.
 */
class FakeClock(initial: Long = 0L) : Clock {
    private var now: Long = initial
    val parks: MutableList<Long> = mutableListOf()

    override fun nowNanos(): Long = now

    override fun parkUntilNanos(target: Long) {
        parks += target
        if (target > now) now = target
    }
}
```

### Step 2.2: Write the failing Pacer test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/ui/PacerTest.kt`:

```kotlin
package ru.alepar.zx80.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.UlaRenderer

class PacerTest {

    private fun newPacer(initialClockTime: Long = 0L): Pair<Pacer, FakeClock> {
        val machine = Spectrum48k() // no reset; synthetic state (all-zero RAM)
        val clock = FakeClock(initialClockTime)
        val pacer = Pacer(machine, UlaRenderer(), clock)
        return pacer to clock
    }

    @Test
    fun `start captures the current clock as startNanos`() {
        val (pacer, clock) = newPacer(initialClockTime = 5_000_000L)
        pacer.start()
        pacer.stepOneFrame()
        assertThat(clock.parks).containsExactly(5_000_000L + 20_000_000L)
    }

    @Test
    fun `stepOneFrame parks at the 20ms target`() {
        val (pacer, clock) = newPacer()
        pacer.start()
        pacer.stepOneFrame()
        assertThat(clock.parks).containsExactly(20_000_000L)
    }

    @Test
    fun `ten stepOneFrame calls produce ten evenly-spaced parks`() {
        val (pacer, clock) = newPacer()
        pacer.start()
        repeat(10) { pacer.stepOneFrame() }
        assertThat(clock.parks).isEqualTo((1..10).map { it * 20_000_000L })
    }

    @Test
    fun `flashOn is false for frames 0 through 15`() {
        val (pacer, _) = newPacer()
        pacer.start()
        assertThat(pacer.flashOn()).isFalse
        repeat(15) { pacer.stepOneFrame() }
        assertThat(pacer.flashOn()).isFalse
    }

    @Test
    fun `flashOn flips to true on frame 16`() {
        val (pacer, _) = newPacer()
        pacer.start()
        repeat(16) { pacer.stepOneFrame() }
        assertThat(pacer.flashOn()).isTrue
    }

    @Test
    fun `flashOn flips back to false on frame 32`() {
        val (pacer, _) = newPacer()
        pacer.start()
        repeat(32) { pacer.stepOneFrame() }
        assertThat(pacer.flashOn()).isFalse
    }

    @Test
    fun `currentImage returns a 256x192 BufferedImage`() {
        val (pacer, _) = newPacer()
        pacer.start()
        pacer.stepOneFrame()
        val img = pacer.currentImage()
        assertThat(img.width).isEqualTo(256)
        assertThat(img.height).isEqualTo(192)
    }

    @Test
    fun `drift-free over 100 frames`() {
        val (pacer, clock) = newPacer()
        pacer.start()
        repeat(100) { pacer.stepOneFrame() }
        assertThat(clock.parks.last()).isEqualTo(100L * 20_000_000L)
    }
}
```

### Step 2.3: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.ui.PacerTest"
```

Expected: compilation failure (`Unresolved reference: Pacer`).

### Step 2.4: Implement Pacer

- [ ] Create `src/main/kotlin/ru/alepar/zx80/ui/Pacer.kt`:

```kotlin
package ru.alepar.zx80.ui

import java.awt.image.BufferedImage
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.UlaRenderer

/**
 * Drives the Spectrum at real-time 50Hz, tracks the flash phase, and produces the latest
 * framebuffer image on demand. Caller owns the loop — Pacer holds no thread, which makes it
 * trivially testable with a fake [Clock].
 *
 * Frame timing is drift-free: each park target is `startNanos + frameNum * FRAME_NS`. Even if
 * individual parks have jitter, the long-run average is exactly 50Hz.
 */
class Pacer(
    private val machine: Spectrum48k,
    private val renderer: UlaRenderer,
    private val clock: Clock = RealClock,
) {
    private var startNanos: Long = 0
    private var frameNum: Long = 0
    private var flashCounter: Int = 0

    /** Capture the start instant and reset counters. Call once before the first stepOneFrame. */
    fun start() {
        startNanos = clock.nowNanos()
        frameNum = 0
        flashCounter = 0
    }

    /** Run one Spectrum frame, advance the flash counter, park until the next 20ms boundary. */
    fun stepOneFrame() {
        machine.runFrame()
        flashCounter++
        frameNum++
        val nextTarget = startNanos + frameNum * FRAME_NS
        clock.parkUntilNanos(nextTarget)
    }

    /** Current flash phase. Toggles every 16 frames (50/16 ~ 3.1 Hz, the real Spectrum rate). */
    fun flashOn(): Boolean = ((flashCounter / 16) and 1) == 1

    /** Latest framebuffer image rendered from current screen RAM using the current flash phase. */
    fun currentImage(): BufferedImage = renderer.render(machine.mem, flashOn())

    companion object {
        const val FRAME_NS = 20_000_000L // 50Hz = 20ms = 20_000_000ns
    }
}
```

### Step 2.5: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.ui.PacerTest"
```

Expected: 8 tests, all PASS.

### Step 2.6: Run the full test suite

- [ ] Run:

```bash
./gradlew test
```

Expected: all tests pass.

### Step 2.7: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/ui/Pacer.kt \
        src/test/kotlin/ru/alepar/zx80/ui/FakeClock.kt \
        src/test/kotlin/ru/alepar/zx80/ui/PacerTest.kt
git commit -m "$(cat <<'EOF'
feat(ui): Pacer — drift-free 50Hz loop with flash phase tracking

Pacer drives Spectrum48k.runFrame at 50Hz wall-clock, computes
park targets as startNanos + frameNum * 20ms (drift-free), and
tracks the FLASH phase counter (toggles every 16 frames). No
internal thread; caller owns the loop (SpectrumWindow next). Tests
use a FakeClock to assert exact park targets and flash transitions
without sleeping.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: SpectrumWindow (M2.4-C)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/ui/SpectrumWindow.kt`

No automated tests for this task. The manual smoke gate in Task 5 verifies the window behavior.

### Step 3.1: Implement SpectrumWindow

- [ ] Create `src/main/kotlin/ru/alepar/zx80/ui/SpectrumWindow.kt`:

```kotlin
package ru.alepar.zx80.ui

import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Swing-based host window that displays the Pacer's framebuffer. Opens a non-resizable JFrame
 * sized 256*scale x 192*scale. Spawns a daemon thread that loops Pacer.stepOneFrame and
 * schedules repaints on the EDT.
 *
 * On window close: signals the pacer thread to stop, waits up to 500ms, disposes the frame,
 * and calls exitProcess(0). Standard emulator shutdown behavior.
 */
class SpectrumWindow(private val pacer: Pacer, private val scale: Int = 2) {
    private val frame = JFrame("ZX Spectrum 48K")
    private val panel =
        object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                g.drawImage(pacer.currentImage(), 0, 0, width, height, null)
            }
        }

    @Volatile private var running = true
    private lateinit var worker: Thread

    fun show() {
        panel.preferredSize = Dimension(256 * scale, 192 * scale)
        frame.isResizable = false
        frame.contentPane.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    running = false
                    worker.join(500)
                    frame.dispose()
                    exitProcess(0)
                }
            }
        )
        worker =
            thread(isDaemon = true, name = "spectrum-pacer") {
                pacer.start()
                while (running) {
                    pacer.stepOneFrame()
                    SwingUtilities.invokeLater { panel.repaint() }
                }
            }
        frame.isVisible = true
    }
}
```

### Step 3.2: Compile-only verification

- [ ] Run:

```bash
./gradlew compileKotlin compileTestKotlin
```

Expected: BUILD SUCCESSFUL. No tests to run; SpectrumWindow is manually verified in Task 5.

### Step 3.3: Run the full test suite as a regression check

- [ ] Run:

```bash
./gradlew test
```

Expected: all existing tests still pass. SpectrumWindow is new code that nothing else references yet.

### Step 3.4: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/ui/SpectrumWindow.kt
git commit -m "$(cat <<'EOF'
feat(ui): SpectrumWindow — JFrame host window driven by Pacer

Non-resizable JFrame at 256*scale x 192*scale. Daemon worker thread
loops Pacer.stepOneFrame and schedules panel.repaint via
SwingUtilities.invokeLater. paintComponent grabs the latest
BufferedImage from Pacer and drawImage scales it to the panel
dimensions. windowClosing: stop pacer, dispose frame, exitProcess(0).

No automated tests — Swing-in-CI is too fraught; manual smoke gate
covers it in the M2.4-E sweep.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: CLI subcommand (M2.4-D)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/cli/SpectrumCommand.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/cli/Main.kt`

### Step 4.1: Implement SpectrumCommand

- [ ] Create `src/main/kotlin/ru/alepar/zx80/cli/SpectrumCommand.kt`:

```kotlin
package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.UlaRenderer
import ru.alepar.zx80.ui.Pacer
import ru.alepar.zx80.ui.SpectrumWindow

/** Launch the ZX Spectrum 48K emulator in a host window at integer scale. */
class SpectrumCommand : CliktCommand(name = "spectrum") {
    private val scale by option("--scale").int().default(2)

    override fun run() {
        val machine = Spectrum48k()
        machine.reset()
        val pacer = Pacer(machine, UlaRenderer())
        val window = SpectrumWindow(pacer, scale)
        window.show()
    }
}
```

### Step 4.2: Register the subcommand in Main.kt

- [ ] Open `src/main/kotlin/ru/alepar/zx80/cli/Main.kt`. Replace the `main` function:

```kotlin
fun main(args: Array<String>) {
    Zx80Cli()
        .subcommands(
            ScoreCommand(),
            RunCommand(),
            DisasmCommand(),
            BenchCommand(),
            ZexdocCommand(),
            SpectrumCommand(),
        )
        .main(args)
}
```

### Step 4.3: Build and verify the CLI registers

- [ ] Run:

```bash
./gradlew installDist
./build/install/zx80/bin/zx80 --help
```

Expected output includes `spectrum` in the subcommands list.

- [ ] Run:

```bash
./build/install/zx80/bin/zx80 spectrum --help
```

Expected: usage text for the `spectrum` command including `--scale=INT`. Exits 0 without launching a window.

### Step 4.4: Run the full test suite

- [ ] Run:

```bash
./gradlew test
```

Expected: all tests still pass. No new unit tests for the CLI (the subcommand has no logic worth unit-testing — just wires components).

### Step 4.5: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/cli/SpectrumCommand.kt \
        src/main/kotlin/ru/alepar/zx80/cli/Main.kt
git commit -m "$(cat <<'EOF'
feat(cli): zx80 spectrum subcommand launches the host window

Constructs Spectrum48k, resets, builds Pacer + UlaRenderer, and
shows a SpectrumWindow at --scale=N (default 2). Wired into Zx80Cli.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Sweep + tag (M2.4-E)

**Files:** none (validation + git tag + push).

### Step 5.1: Run the full check

- [ ] Run:

```bash
./gradlew clean check installDist
```

Expected: BUILD SUCCESSFUL.

### Step 5.2: Run the score harness for regression check

- [ ] Run:

```bash
./build/install/zx80/bin/zx80 score
```

Expected:
- programs: 5/5 PASS
- fuse: 1354/1356
- ZEXDOC: 0 IllegalStateException, 0 ERROR
- composite SCORE: ≥ 0.966

### Step 5.3: Headless CLI smoke (CI-safe)

- [ ] Run:

```bash
./build/install/zx80/bin/zx80 spectrum --help
```

Expected: prints usage and exits 0 without launching a window.

### Step 5.4: Manual window smoke (DISPLAY required)

This step requires a graphical session. If the executor is headless, document the limitation in the commit message at step 5.6 and skip this gate; the user will run it manually before merging.

- [ ] Run:

```bash
./build/install/zx80/bin/zx80 spectrum
```

Expected:
- A 512×384 window opens titled "ZX Spectrum 48K".
- Window is non-resizable (drag handles missing).
- Displayed image is whatever the stuck ROM has written to screen RAM (per `zx80-1qc`: likely all-black).
- JVM CPU usage is steady at low percentage (not pegged at 100%) — the Pacer is parking between frames.
- Clicking the close (X) button cleanly exits the CLI within 1 second. No hung process.

Record the observed outcome (or "headless executor — skipped") for the commit message at step 5.6.

### Step 5.5: Tag the milestone

- [ ] Apply the tag:

```bash
git tag -a m2-phase01-4 -m "M2.4: Display backend — Swing host window at real-time 50Hz"
git tag --list | grep m2-phase01-4
```

Expected: `m2-phase01-4` listed.

### Step 5.6: Close the beads issue and push

- [ ] Claim and close M2.4 in beads, then push:

```bash
bd update zx80-rdw --claim
bd close zx80-rdw --reason="M2.4 complete: Clock + Pacer (drift-free 50Hz, FakeClock-tested) + SpectrumWindow (JFrame at 2x scale) + zx80 spectrum CLI. Tag m2-phase01-4. SCORE preserved >=0.966. Manual smoke: <PASTE OUTCOME OR 'headless executor, skipped'>."
git pull --rebase
bd dolt push
git push
git push --tags
git status
```

Expected: `git status` shows `On branch opus-4.7` and `up to date with 'origin/opus-4.7'`. Tag pushed.

---

## Self-review notes (recorded after writing the plan)

**Spec coverage check:**

| Spec section | Task |
|---|---|
| M2.4-A Clock + RealClock | Task 1 |
| M2.4-B Pacer + FakeClock + tests | Task 2 |
| M2.4-C SpectrumWindow | Task 3 |
| M2.4-D CLI subcommand | Task 4 |
| M2.4-E Sweep + manual smoke + tag | Task 5 |
| Validation gates 1-9 | Task 5 (steps 5.1-5.6) |

**No-placeholder check** — every step contains executable code or commands. Step 5.6 has a placeholder in the close-reason string (`<PASTE OUTCOME OR 'headless executor, skipped'>`) which is explicitly described as runtime substitution, not a code placeholder.

**Type/name consistency** — `Clock`, `RealClock`, `Pacer`, `Pacer.start/stepOneFrame/flashOn/currentImage/FRAME_NS`, `SpectrumWindow(pacer, scale)`, `SpectrumCommand`, `--scale` CLI option — used identically across spec, plan, and tests.
