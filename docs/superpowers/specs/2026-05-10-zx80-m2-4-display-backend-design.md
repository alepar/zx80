# M2.4: Display Backend — Swing Host Window — Design

## Goal

Add a host-window display backend that boots `Spectrum48k`, drives
`runFrame()` at real-time 50Hz, tracks the flash phase, and paints the
rendered framebuffer in a 2×-scaled Swing window. New `zx80 spectrum`
CLI subcommand.

## Context

Beads issue: `zx80-rdw` (parent: `zx80-48f` M2 epic).

After M2.1-M2.3, the machine boots the ROM, runs frames, fires
interrupts, and renders screen RAM into a `BufferedImage`. M2.4 closes
the loop: real-time pacing, host window, the first thing a user can
actually run interactively.

Known caveat: per `zx80-1qc`, the Sinclair ROM init stalls at
PC=0x11E6 due to incomplete opcode coverage (1496/1792). When the user
runs `zx80 spectrum`, the window will open and the Pacer will hit 50Hz,
but the displayed image will likely be all-black — the ROM never reaches
the BASIC ready loop. M2.4 deliverables don't depend on the ROM
reaching the boot screen. Phase H (`zx80-6q9`) will fill missing slots
and unstick the ROM, after which `zx80 spectrum` shows the real © boot.

## Scope

### M2.4-A: Clock abstraction

New `ui/Clock.kt`:

```kotlin
package ru.alepar.zx80.ui

import java.util.concurrent.locks.LockSupport

/**
 * Wall-clock + park abstraction. Production uses [RealClock]; tests inject a fake clock to
 * verify drift-free pacing without sleeping. `LockSupport.parkNanos` is used over `Thread.sleep`
 * because the latter rounds up to 1ms before calling the VM and discards sub-ms precision; on
 * Linux with hrtimers, parkNanos gives ~50µs accuracy.
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

### M2.4-B: Pacer

New `ui/Pacer.kt`:

```kotlin
package ru.alepar.zx80.ui

import java.awt.image.BufferedImage
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.UlaRenderer

/**
 * Drives the Spectrum at real-time 50Hz, tracks the flash phase, and produces the latest
 * framebuffer image on demand. Caller owns the loop — Pacer holds no thread itself, which makes
 * it trivially testable with a fake [Clock].
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

    /** Current flash phase. Toggles every 16 frames (50/16 ≈ 3.1 Hz, the real Spectrum rate). */
    fun flashOn(): Boolean = ((flashCounter / 16) and 1) == 1

    /** Latest framebuffer image rendered from current screen RAM using the current flash phase. */
    fun currentImage(): BufferedImage = renderer.render(machine.mem, flashOn())

    companion object {
        const val FRAME_NS = 20_000_000L // 50Hz = 20ms = 20_000_000ns
    }
}
```

### M2.4-C: SpectrumWindow

New `ui/SpectrumWindow.kt`:

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
 * sized 256·scale × 192·scale. Spawns a daemon thread that loops Pacer.stepOneFrame and
 * schedules repaints on the EDT.
 *
 * On window close: signals the pacer thread to stop, waits up to 500ms, disposes the frame,
 * and calls exitProcess(0). Standard emulator shutdown behavior.
 */
class SpectrumWindow(private val pacer: Pacer, private val scale: Int = 2) {
    private val frame = JFrame("ZX Spectrum 48K")
    private val panel = object : JPanel() {
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
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                running = false
                worker.join(500)
                frame.dispose()
                exitProcess(0)
            }
        })
        worker = thread(isDaemon = true, name = "spectrum-pacer") {
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

### M2.4-D: CLI subcommand

New `cli/SpectrumCommand.kt`:

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

Registered in `cli/Main.kt`'s `Zx80Cli` subcommand list.

### M2.4-E: Sweep + manual smoke + tag

Standard sweep with one human-in-the-loop step: launch `zx80 spectrum`,
verify the window opens, runs at roughly 50Hz CPU usage, and closes
cleanly via the X button. Tag `m2-phase01-4`.

### Out of scope

- Keyboard input (M2.5)
- Border rendering (M2.8)
- Beeper audio (M2.6)
- Pause/resume controls
- Save-state / snapshots (M3)
- Fullscreen / resize / fractional scale
- High-DPI awareness / per-monitor scaling
- Replay / rewind

## Architecture

```
src/main/kotlin/ru/alepar/zx80/
  ui/                          NEW PACKAGE
    Clock.kt                   NEW
    Pacer.kt                   NEW
    SpectrumWindow.kt          NEW
  cli/SpectrumCommand.kt       NEW
  cli/Main.kt                  MODIFY (register the subcommand)

src/test/kotlin/ru/alepar/zx80/
  ui/                          NEW PACKAGE
    FakeClock.kt               NEW (test helper)
    ClockTest.kt               NEW
    PacerTest.kt               NEW
```

**Why a new `ui/` package.** `machine/` is platform-neutral — Memory,
Cpu, FrameScheduler, UlaRenderer. The Pacer's job (real-time wall-clock
loop) and the SpectrumWindow's job (Swing) are host-platform concerns.
Splitting keeps `machine/` portable and isolates Swing/AWT to one
package.

**Threading model.**

- The Pacer thread (worker, daemon) calls `pacer.stepOneFrame()` in a
  tight loop. After each frame it schedules `panel.repaint()` via
  `SwingUtilities.invokeLater` (no direct EDT cross-thread call).
- The EDT runs `paintComponent`, which calls `pacer.currentImage()` and
  `g.drawImage`. `currentImage()` does a fresh `UlaRenderer.render()`;
  the returned BufferedImage is a new instance per call, so there's no
  shared-mutable-state race with the worker.
- On `windowClosing` (EDT): set `running = false`, join the worker
  briefly, dispose the frame, `exitProcess(0)`.

**Why no internal thread in Pacer.** Tests need to drive the pacer
synchronously, frame-by-frame, with a fake clock. Putting the thread in
SpectrumWindow keeps the Pacer testable without `Thread.sleep` or
join-and-wait gymnastics.

## Test strategy

### FakeClock.kt (test helper, no test class)

```kotlin
package ru.alepar.zx80.ui

class FakeClock(initial: Long = 0L) : Clock {
    private var now: Long = initial
    val parks: MutableList<Long> = mutableListOf()
    override fun nowNanos(): Long = now
    override fun parkUntilNanos(target: Long) {
        parks += target
        if (target > now) now = target
    }
    fun advanceTo(target: Long) { if (target > now) now = target }
}
```

Snap-to-target behavior: `parkUntilNanos` always lands you exactly at
the target. This models drift-free pacing in tests.

### ClockTest.kt — 2 assertions

- `RealClock.nowNanos` is monotonically non-decreasing across two
  consecutive calls.
- `RealClock.parkUntilNanos(now + 1_000_000)` (1ms target) returns
  within 50ms wall-clock. Loose upper bound, robust to CI load.

### PacerTest.kt — 8 assertions

Common setup:

```kotlin
private fun newPacer(initialClockTime: Long = 0L): Pair<Pacer, FakeClock> {
    val machine = Spectrum48k()                     // no reset; synthetic state
    machine.mem.write(0x4000, 0x00)                 // touch RAM (no-op write)
    val clock = FakeClock(initialClockTime)
    val pacer = Pacer(machine, UlaRenderer(), clock)
    return pacer to clock
}
```

Tests:

1. **start captures the current clock as startNanos.** From `FakeClock(5_000_000)`,
   `start(); stepOneFrame()` → first park target == `5_000_000 + 20_000_000`.
2. **stepOneFrame parks at the 20ms target.** From t=0, after one
   stepOneFrame, `clock.parks == listOf(20_000_000L)`.
3. **10 stepOneFrame calls produce 10 evenly-spaced parks.** From t=0,
   `clock.parks == (1..10).map { it * 20_000_000L }`.
4. **flashOn is false for frames 0-15.** From fresh start, `flashOn()`
   is false. After 15 stepOneFrame, still false.
5. **flashOn flips to true on frame 16.** After 16 stepOneFrame,
   `flashOn() == true`. Still true after 17, 31.
6. **flashOn flips back to false on frame 32.** After 32 stepOneFrame,
   `flashOn() == false`.
7. **currentImage returns a 256x192 BufferedImage.** After start +
   1 stepOneFrame, `currentImage().width == 256 && currentImage().height
   == 192`.
8. **drift-free over 100 frames.** From t=0, after 100 stepOneFrame,
   `clock.parks.last() == 2_000_000_000L`. (Even if individual parks
   had jitter, target-time anchoring keeps cumulative target exact.)

About 10 assertions across 2 test files.

### SpectrumWindow

No automated tests. Swing in CI introduces too much friction
(`HeadlessException`, EDT join races, X11 dependencies in the test
host). Manual smoke gate covers it (gate 8 below).

## Validation gates (WU M2.4-E)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. New tests pass (~10 assertions).
3. Existing M1 + M2.1 + M2.2 + M2.3 tests still pass.
4. ZEXDOC: 0 IllegalStateException, 0 ERROR.
5. FUSE: 1354/1356.
6. Programs: 5/5.
7. Composite SCORE: ≥ 0.966.
8. **Manual smoke (DISPLAY required, NOT a CI gate):**
   `./build/install/zx80/bin/zx80 spectrum` opens a 512×384 window
   titled "ZX Spectrum 48K", non-resizable. The displayed image is
   whatever the stuck ROM has written (likely all-black). Closing the
   window via the X button cleanly exits the CLI within 1 second. JVM
   CPU usage is bounded (not pegged at 100%). Document the outcome in
   the M2.4-E commit message.
9. **Headless CLI registration check (CI-safe):**
   `./build/install/zx80/bin/zx80 spectrum --help` prints usage and
   exits 0 without launching a window. Confirms CLI registration didn't
   break headless invocation.

Tag: `m2-phase01-4`.

## Work-unit breakdown

| WU | Description |
|---|---|
| M2.4-A | `ui/Clock.kt` interface + `RealClock` object + `ClockTest`. |
| M2.4-B | `ui/Pacer.kt` + `ui/FakeClock.kt` test helper + `PacerTest`. |
| M2.4-C | `ui/SpectrumWindow.kt` — JFrame + JPanel + paintComponent + daemon thread + windowClosing handler. No automated tests. |
| M2.4-D | `cli/SpectrumCommand.kt` + register in `cli/Main.kt`. CLI smoke via `--help`. |
| M2.4-E | Sweep: clean check, score harness regression, manual `zx80 spectrum` smoke, tag `m2-phase01-4`, close `zx80-rdw`, push. |

Within-phase deps: A → B (B uses Clock). C depends on B (Pacer). D
depends on C. E depends on all. Linear order recommended.

## Risks

- **`LockSupport.parkNanos` precision under load.** Linux hrtimers give
  ~50µs precision under normal load; under heavy load jitter can hit
  several ms. The drift-free target-time anchoring absorbs all of this
  — long-run average is exactly 50Hz regardless. Per-frame jitter is
  invisible to the eye.
- **Swing thread safety.** `pacer.currentImage()` is called from the
  EDT (inside `paintComponent`); `pacer.stepOneFrame()` runs on the
  worker thread. Both access `machine.mem` indirectly via the renderer.
  Theoretically a worker write to mem could overlap with the EDT read.
  In practice: the worker writes to mem inside `runFrame()`, then
  schedules a repaint via `invokeLater` (which happens-before the EDT's
  paint). The pair forms a happens-before edge through the AWT event
  queue, so the EDT's view of mem is consistent with the just-completed
  frame. If torn reads ever surface (visible artifact mid-redraw), we
  switch to double-buffering — render into a fresh BufferedImage on the
  worker, hand it to EDT via a volatile reference.
- **Headless test environments.** Constructing a `BufferedImage` works
  headless. Constructing a `JFrame` does NOT in strictly headless mode
  — throws `HeadlessException`. The Pacer + Clock tests don't touch
  JFrame. Gate 9 confirms the CLI doesn't instantiate Swing at
  argument-parse time.
- **`exitProcess(0)` on window close vs Clikt cleanup.** The
  `windowClosing` handler force-exits the JVM, bypassing any Clikt
  cleanup. For this emulator there's nothing to clean up. If we later
  want non-zero exit codes on emulation crash, we'd switch to a flag
  the main thread reads.
- **ROM stuck at PC=0x11E6.** Documented in context above. M2.4 ships
  the visible-but-blank window; Phase H (`zx80-6q9`) will give it
  meaningful content.
