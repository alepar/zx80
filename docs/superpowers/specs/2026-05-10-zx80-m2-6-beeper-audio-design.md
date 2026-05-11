# M2.6: Beeper Audio (1-bit PCM) — Design

## Goal

Capture OUT(0xFE) bit-4 writes (the Spectrum beeper), render them into
48 kHz 8-bit unsigned PCM, and feed the audio through
`javax.sound.sampled.SourceDataLine` in 20 ms (960-sample) frames
driven by the existing Pacer loop. Graceful degrade to silent when no
audio device is available, plus a `--no-audio` CLI flag.

## Context

Beads issue: `zx80-h4f` (parent: `zx80-48f` M2 epic).

After M2.5, the SpectrumIoBus handles port 0xFE reads (keyboard) but
its write method is still a no-op stub. The Spectrum's beeper is bit 4
of OUT(0xFE) — a 1-bit speaker that programs toggle in software to
produce square-wave tones at audio frequencies. M2.6 implements the
write side for the beeper. Border color (bits 0-2) stays stubbed for
M2.8.

Spectrum CPU runs at 3.5 MHz; one frame is 69_888 T-states; we want 48
kHz audio = 960 samples per frame. Sample-to-T-state ratio is
`69_888 / 960 = 72.8` (irrational division; we use floating-point in
render but the inputs are integer T-state offsets, so output is
deterministic per (events, T-state) input).

Known caveat: per `zx80-1qc`, the Sinclair ROM is stuck at PC=0x11E6
and never reaches the BASIC ready loop. The user can't trigger the
beeper through BASIC's BEEP statement yet. M2.6 plumbing is verified
by unit tests; manual smoke only confirms "no crash, no audio
warnings."

## Scope

### M2.6-A: Beeper

New `machine/Beeper.kt`:

```kotlin
package ru.alepar.zx80.machine

import ru.alepar.zx80.cpu.Cpu

/**
 * Captures OUT(0xFE) bit-4 toggles (the Spectrum's 1-bit beeper) and renders them to a 48 kHz
 * 8-bit unsigned PCM buffer once per frame.
 *
 * Single-threaded (Pacer thread only): SpectrumIoBus.write calls [onWrite] during runFrame;
 * Pacer's audio sink calls [beginFrame] before runFrame and [render] after.
 *
 * `events` holds T-state offsets (relative to frame start) at which the bit toggled. We don't
 * need to record the new value — toggles always flip; the starting [bit] is the post-render
 * carry-over.
 */
class Beeper(private val cpu: Cpu) {
    private val events = mutableListOf<Long>()
    private var bit: Int = 0
    private var frameStartTStates: Long = 0L

    /** Called from SpectrumIoBus.write when OUT(0xFE) bit 4 changes. */
    fun onWrite(newBit: Int) {
        val b = newBit and 1
        if (b != bit) {
            events.add(cpu.tStates - frameStartTStates)
            bit = b
        }
    }

    /** Called at start of each frame. Resets event log; preserves current [bit] for carry-over. */
    fun beginFrame() {
        frameStartTStates = cpu.tStates
        events.clear()
    }

    /**
     * Render 960 samples for the just-completed frame into [buf]. The starting bit is the value
     * of [bit] at frame begin (carried from the previous frame); each event flips it at the
     * sample whose T-state >= the event offset.
     */
    fun render(buf: ByteArray) {
        require(buf.size >= SAMPLES_PER_FRAME) { "buf must hold >= $SAMPLES_PER_FRAME samples" }
        // Sample n maps to T-state offset (n * T_STATES_PER_FRAME / SAMPLES_PER_FRAME).
        // Use floating-point division; inputs are integer; outputs are deterministic per input.
        var startBit = startBitForRender()
        var eventIdx = 0
        for (sample in 0 until SAMPLES_PER_FRAME) {
            val tStateAtSample = (sample.toDouble() * T_STATES_PER_FRAME / SAMPLES_PER_FRAME).toLong()
            while (eventIdx < events.size && events[eventIdx] <= tStateAtSample) {
                startBit = startBit xor 1
                eventIdx++
            }
            buf[sample] = if (startBit == 1) AMP_HI else AMP_LO
        }
    }

    /**
     * The bit at sample 0 of the current frame's render: it's the [bit] value AFTER all events,
     * carried over from the previous frame. We track this implicitly by NOT resetting [bit] in
     * [beginFrame] — the post-render value naturally carries.
     */
    private fun startBitForRender(): Int = bit xor (events.size and 1)

    companion object {
        const val SAMPLES_PER_FRAME = 960
        const val T_STATES_PER_FRAME = 69_888
        val AMP_HI: Byte = 0xA0.toByte()
        val AMP_LO: Byte = 0x60.toByte()
    }
}
```

Note the `startBitForRender` math: at frame-end, `bit` is the post-toggle
value (set by the last `onWrite`). For the NEXT render, sample 0's bit is
the value at frame-begin, BEFORE any events fired — that's `bit XOR
(events.size and 1)` (the count of toggles tells us how to "un-toggle"
back to the start).

### M2.6-B: AudioOutput

New `ui/AudioOutput.kt`:

```kotlin
package ru.alepar.zx80.ui

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

/**
 * Sink for rendered PCM samples. Implementations: [JavaSoundAudioOutput] feeds a real device;
 * [NoOpAudioOutput] silently swallows. Use [tryOpen] to get the right one for the host.
 */
interface AudioOutput {
    fun push(buf: ByteArray, length: Int)
    fun close()

    companion object {
        private const val SAMPLE_RATE = 48_000f
        private const val SAMPLE_SIZE_BITS = 8
        private const val CHANNELS = 1
        private const val BUFFER_FRAMES = 4 // ~80ms of buffer (4 * 960 samples)

        /**
         * Try to open a 48 kHz 8-bit unsigned mono SourceDataLine. On any failure
         * (LineUnavailableException, device absent, format unsupported), log a warning to
         * stderr and return [NoOpAudioOutput] so emulation continues silently.
         */
        fun tryOpen(): AudioOutput {
            return try {
                val format =
                    AudioFormat(
                        AudioFormat.Encoding.PCM_UNSIGNED,
                        SAMPLE_RATE,
                        SAMPLE_SIZE_BITS,
                        CHANNELS,
                        1, // frame size in bytes (1 channel * 1 byte)
                        SAMPLE_RATE,
                        false, // little-endian (8-bit doesn't matter)
                    )
                val line = AudioSystem.getSourceDataLine(format)
                line.open(format, 960 * BUFFER_FRAMES)
                line.start()
                JavaSoundAudioOutput(line)
            } catch (e: LineUnavailableException) {
                System.err.println("WARN: audio device unavailable; running silently (${e.message})")
                NoOpAudioOutput
            } catch (e: IllegalArgumentException) {
                System.err.println("WARN: audio format unsupported; running silently (${e.message})")
                NoOpAudioOutput
            }
        }
    }
}

class JavaSoundAudioOutput(private val line: SourceDataLine) : AudioOutput {
    override fun push(buf: ByteArray, length: Int) {
        line.write(buf, 0, length)
    }
    override fun close() {
        line.drain()
        line.stop()
        line.close()
    }
}

object NoOpAudioOutput : AudioOutput {
    override fun push(buf: ByteArray, length: Int) { /* swallow */ }
    override fun close() {}
}
```

### M2.6-C: AudioSink

New `ui/AudioSink.kt`:

```kotlin
package ru.alepar.zx80.ui

import ru.alepar.zx80.machine.Beeper

/**
 * Per-frame hook called by [Pacer] around runFrame. Tests can use [NoOpAudioSink];
 * production uses [BeeperAudioSink].
 */
interface AudioSink {
    fun beforeFrame()
    fun afterFrame()
}

object NoOpAudioSink : AudioSink {
    override fun beforeFrame() {}
    override fun afterFrame() {}
}

class BeeperAudioSink(private val beeper: Beeper, private val out: AudioOutput) : AudioSink {
    private val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    override fun beforeFrame() { beeper.beginFrame() }
    override fun afterFrame() {
        beeper.render(buf)
        out.push(buf, buf.size)
    }
}
```

### Pacer modification

In `ui/Pacer.kt`, add an `audioSink` constructor parameter and call it
around `runFrame`:

```kotlin
class Pacer(
    private val machine: Spectrum48k,
    private val renderer: UlaRenderer,
    private val clock: Clock = RealClock,
    private val audioSink: AudioSink = NoOpAudioSink,
) {
    // ... existing fields ...
    fun stepOneFrame() {
        audioSink.beforeFrame()
        machine.runFrame()
        audioSink.afterFrame()
        flashCounter++
        frameNum++
        val nextTarget = startNanos + frameNum * FRAME_NS
        clock.parkUntilNanos(nextTarget)
    }
}
```

Existing PacerTests pass the 3-arg constructor (no `audioSink`), so
they default to `NoOpAudioSink` and remain green.

### M2.6-D: SpectrumIoBus extension

In `machine/SpectrumIoBus.kt`, modify the constructor and `write`:

```kotlin
class SpectrumIoBus(private val keyboard: Keyboard, private val beeper: Beeper) : IoBus {
    override fun read(port: Int): Int = /* unchanged */

    override fun write(port: Int, value: Int) {
        if ((port and 0x01) == 0) {
            beeper.onWrite((value ushr 4) and 1)
            // Border color (bits 0-2) stays stubbed for M2.8.
        }
    }
}
```

### M2.6-E: SpectrumCommand wiring + --no-audio flag

```kotlin
class SpectrumCommand : CliktCommand(name = "spectrum") {
    private val scale by option("--scale").int().default(2)
    private val noAudio by option("--no-audio", help = "Disable audio output").flag()

    override fun run() {
        val machine = Spectrum48k()
        val keyboard = Keyboard()
        val beeper = Beeper(machine.cpu)
        machine.cpu.io = SpectrumIoBus(keyboard, beeper)
        machine.reset()
        val audioOut = if (noAudio) NoOpAudioOutput else AudioOutput.tryOpen()
        val audioSink = BeeperAudioSink(beeper, audioOut)
        val pacer = Pacer(machine, UlaRenderer(), audioSink = audioSink)
        val window = SpectrumWindow(pacer, keyboard, scale)
        window.show()
    }
}
```

The line `machine.cpu.io = SpectrumIoBus(keyboard, beeper)` requires the
M2.5 SpectrumIoBus constructor signature update. Bus is installed
BEFORE reset (forward-compatible insurance).

### Out of scope

- Anti-aliasing / band-limiting filter (raw 1-bit sounds like a real Spectrum)
- Stereo / spatial effects
- Audio recording to WAV
- Tape input (EAR / MIC) — M3
- AY-3-8912 chip (128K model) — M3+
- Dynamic sample rate auto-detection
- Audio buffer underrun detection / reporting

## Architecture

```
src/main/kotlin/ru/alepar/zx80/
  machine/Beeper.kt           NEW
  machine/SpectrumIoBus.kt    MODIFY (ctor + write())
  ui/AudioOutput.kt           NEW (interface + JavaSound + NoOp + tryOpen)
  ui/AudioSink.kt             NEW (interface + NoOpAudioSink + BeeperAudioSink)
  ui/Pacer.kt                 MODIFY (audioSink ctor arg + before/after calls)
  cli/SpectrumCommand.kt      MODIFY (wire Beeper + AudioOutput + --no-audio)
src/test/kotlin/ru/alepar/zx80/
  machine/BeeperTest.kt           NEW
  machine/SpectrumIoBusTest.kt    EXTEND
  ui/RecordingAudioOutput.kt      NEW (test helper)
  ui/AudioOutputTest.kt           NEW
  ui/BeeperAudioSinkTest.kt       NEW
  ui/PacerTest.kt                 EXTEND
```

**Threading model.** `SpectrumIoBus.write`, `Beeper.onWrite`,
`Beeper.beginFrame`, `Beeper.render`, `AudioSink.before/afterFrame` all
run on the Pacer thread (worker daemon thread inside SpectrumWindow).
`AudioOutput.push` (which calls `SourceDataLine.write`) also runs on the
Pacer thread; SourceDataLine itself is internally synchronized and reads
from its buffer on the OS audio device's own thread. No locks needed in
our code.

**Sample resolution math.** `T_PER_SAMPLE = 69_888.0 / 960 ≈ 72.8`. Use
floating-point in the loop; integer inputs make output deterministic per
(events, T-state) input.

**Format choice: 8-bit unsigned mono.**
- Half the bytes of 16-bit (negligible at 48 kHz).
- 1-bit beeper has no dynamic range to represent; 8 bits is plenty.
- AMP_HI=0xA0 / AMP_LO=0x60 gives ~25% swing around center (0x80).

## Test strategy

### BeeperTest — 7 assertions

```kotlin
private fun newBeeper(): Pair<Beeper, Cpu> {
    val cpu = Cpu()
    return Beeper(cpu) to cpu
}

@Test fun `no events render constant AMP_LO buffer`() {
    val (beeper, _) = newBeeper()
    beeper.beginFrame()
    val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    beeper.render(buf)
    for (b in buf) assertThat(b).isEqualTo(0x60.toByte())
}

@Test fun `single mid-frame toggle to 1: first half AMP_LO, second half AMP_HI`() {
    val (beeper, cpu) = newBeeper()
    beeper.beginFrame()
    cpu.tStates += 34_944 // half a frame
    beeper.onWrite(1)
    val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    beeper.render(buf)
    assertThat(buf[0]).isEqualTo(0x60.toByte())
    assertThat(buf[479]).isEqualTo(0x60.toByte())
    assertThat(buf[480]).isEqualTo(0xA0.toByte())
    assertThat(buf[959]).isEqualTo(0xA0.toByte())
}

@Test fun `two toggles produce three regions`() {
    val (beeper, cpu) = newBeeper()
    beeper.beginFrame()
    cpu.tStates += 17_472 // 1/4 frame
    beeper.onWrite(1)
    cpu.tStates += 34_944 // another 1/2 frame -> 3/4 mark
    beeper.onWrite(0)
    val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    beeper.render(buf)
    // 0..239 LO, 240..719 HI, 720..959 LO
    assertThat(buf[0]).isEqualTo(0x60.toByte())
    assertThat(buf[239]).isEqualTo(0x60.toByte())
    assertThat(buf[240]).isEqualTo(0xA0.toByte())
    assertThat(buf[719]).isEqualTo(0xA0.toByte())
    assertThat(buf[720]).isEqualTo(0x60.toByte())
    assertThat(buf[959]).isEqualTo(0x60.toByte())
}

@Test fun `cross-frame state carries: last bit of frame N becomes sample 0 of frame N+1`() {
    val (beeper, cpu) = newBeeper()
    beeper.beginFrame()
    cpu.tStates += 34_944
    beeper.onWrite(1)
    val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    beeper.render(buf)
    // Frame N+1: no events, bit stays at 1 across the whole frame.
    beeper.beginFrame()
    cpu.tStates += 69_888
    beeper.render(buf)
    for (b in buf) assertThat(b).isEqualTo(0xA0.toByte())
}

@Test fun `idempotent onWrite: same bit twice records only one event`() {
    val (beeper, cpu) = newBeeper()
    beeper.beginFrame()
    cpu.tStates += 17_472
    beeper.onWrite(1)
    cpu.tStates += 34_944
    beeper.onWrite(1) // same value — no event
    val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    beeper.render(buf)
    // 0..239 LO, 240..959 HI
    assertThat(buf[239]).isEqualTo(0x60.toByte())
    assertThat(buf[240]).isEqualTo(0xA0.toByte())
    assertThat(buf[959]).isEqualTo(0xA0.toByte())
}

@Test fun `edge-of-frame toggle at offset 0`() {
    val (beeper, _) = newBeeper()
    beeper.beginFrame()
    beeper.onWrite(1) // toggle at t=0
    val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    beeper.render(buf)
    for (b in buf) assertThat(b).isEqualTo(0xA0.toByte())
}

@Test fun `multiple frames retain bit across boundaries`() {
    val (beeper, cpu) = newBeeper()
    val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    // Frame 1: toggle to 1 mid-frame.
    beeper.beginFrame()
    cpu.tStates += 34_944
    beeper.onWrite(1)
    beeper.render(buf)
    // Frame 2: toggle back to 0 at 1/4.
    beeper.beginFrame()
    cpu.tStates += 17_472
    beeper.onWrite(0)
    cpu.tStates += 52_416 // rest of frame
    beeper.render(buf)
    // 0..239 HI (carried from frame 1), 240..959 LO.
    assertThat(buf[0]).isEqualTo(0xA0.toByte())
    assertThat(buf[239]).isEqualTo(0xA0.toByte())
    assertThat(buf[240]).isEqualTo(0x60.toByte())
    assertThat(buf[959]).isEqualTo(0x60.toByte())
}
```

### SpectrumIoBusTest extension — 2 assertions

```kotlin
@Test fun `write to ULA port with bit 4 set notifies beeper of bit 1`() {
    val cpu = Cpu()
    val beeper = Beeper(cpu)
    val bus = SpectrumIoBus(Keyboard(), beeper)
    beeper.beginFrame()
    bus.write(0xFEFE, 0x10) // bit 4 set
    val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    beeper.render(buf)
    assertThat(buf[0]).isEqualTo(0xA0.toByte())
    assertThat(buf[Beeper.SAMPLES_PER_FRAME - 1]).isEqualTo(0xA0.toByte())
}

@Test fun `write to ULA port with bit 4 clear leaves beeper at bit 0`() {
    val cpu = Cpu()
    val beeper = Beeper(cpu)
    val bus = SpectrumIoBus(Keyboard(), beeper)
    beeper.beginFrame()
    bus.write(0xFEFE, 0x07) // bit 4 clear; border bits set
    val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    beeper.render(buf)
    assertThat(buf[0]).isEqualTo(0x60.toByte())
}
```

### AudioOutputTest — 2 assertions

```kotlin
@Test fun `NoOpAudioOutput push does not throw`() {
    NoOpAudioOutput.push(ByteArray(960), 960)
}

@Test fun `NoOpAudioOutput close does not throw`() {
    NoOpAudioOutput.close()
}
```

We do NOT test `JavaSoundAudioOutput` directly — opening a real
SourceDataLine requires a device. `AudioOutput.tryOpen` in CI falls
through to NoOp via the catch blocks; manual smoke verifies real audio.

### RecordingAudioOutput (test helper)

```kotlin
class RecordingAudioOutput : AudioOutput {
    val pushed: MutableList<ByteArray> = mutableListOf()
    override fun push(buf: ByteArray, length: Int) { pushed += buf.copyOf(length) }
    override fun close() {}
}
```

### BeeperAudioSinkTest — 3 assertions

```kotlin
@Test fun `beforeFrame begins a new beeper frame and clears events`() {
    val cpu = Cpu()
    val beeper = Beeper(cpu)
    val sink = BeeperAudioSink(beeper, NoOpAudioOutput)
    // Set bit, advance time, sink.beforeFrame should reset frame markers.
    beeper.beginFrame()
    beeper.onWrite(1)
    cpu.tStates += 1000
    sink.beforeFrame()
    // After beforeFrame, the events list is cleared; render produces all-AMP_HI
    // (bit carried as 1 from prior toggle).
    val buf = ByteArray(Beeper.SAMPLES_PER_FRAME)
    cpu.tStates += 69_888
    beeper.render(buf)
    for (b in buf) assertThat(b).isEqualTo(0xA0.toByte())
}

@Test fun `afterFrame renders to AudioOutput`() {
    val cpu = Cpu()
    val beeper = Beeper(cpu)
    val out = RecordingAudioOutput()
    val sink = BeeperAudioSink(beeper, out)
    sink.beforeFrame()
    cpu.tStates += 69_888
    sink.afterFrame()
    assertThat(out.pushed).hasSize(1)
    assertThat(out.pushed[0]).hasSize(Beeper.SAMPLES_PER_FRAME)
}

@Test fun `two frames produce two buffers`() {
    val cpu = Cpu()
    val beeper = Beeper(cpu)
    val out = RecordingAudioOutput()
    val sink = BeeperAudioSink(beeper, out)
    sink.beforeFrame()
    cpu.tStates += 69_888
    sink.afterFrame()
    sink.beforeFrame()
    cpu.tStates += 69_888
    sink.afterFrame()
    assertThat(out.pushed).hasSize(2)
}
```

### PacerTest extension — 1 assertion (the recording-sink call-order one)

```kotlin
@Test fun `audioSink beforeFrame and afterFrame fire around each runFrame`() {
    class RecordingAudioSink : AudioSink {
        val log: MutableList<String> = mutableListOf()
        override fun beforeFrame() { log += "before" }
        override fun afterFrame() { log += "after" }
    }
    val machine = Spectrum48k()
    val clock = FakeClock()
    val sink = RecordingAudioSink()
    val pacer = Pacer(machine, UlaRenderer(), clock, sink)
    pacer.start()
    pacer.stepOneFrame()
    pacer.stepOneFrame()
    assertThat(sink.log).containsExactly("before", "after", "before", "after")
}
```

Existing 8 PacerTest assertions continue to work because Pacer's
`audioSink` parameter defaults to `NoOpAudioSink`.

**Total: ~16 new assertions across 3 new + 2 extended test files.**

## Validation gates (WU M2.6-F)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. All new tests pass.
3. Existing tests still pass (default AudioSink keeps PacerTest untouched).
4. ZEXDOC clean (0 IllegalStateException, 0 ERROR).
5. FUSE: 1354/1356.
6. Programs: 5/5.
7. Composite SCORE: ≥0.966.
8. **Headless CLI gate:** `./build/install/zx80/bin/zx80 spectrum --help`
   shows `--no-audio` option and exits 0.
9. **Headless run gate:** Manual gate — the CLI should not crash on a
   headless executor with `--no-audio`. We can't `zx80 spectrum
   --no-audio` in CI because the window opening triggers
   HeadlessException. The check is "the CLI registration is sound";
   verified by gate 8 listing the flag.
10. **Manual smoke (DISPLAY + audio required, deferred):** `zx80
    spectrum` opens the window and the audio device opens without
    warning. ROM is still stuck at 0x11E6 so no audible output, but no
    crashes / no audio underrun warnings.

Tag: `m2-phase01-6`.

## Work-unit breakdown

| WU | Description |
|---|---|
| M2.6-A | `Beeper` class + `BeeperTest` (7 assertions). |
| M2.6-B | `AudioOutput` interface + `JavaSoundAudioOutput` + `NoOpAudioOutput` + `AudioOutput.tryOpen` factory + `AudioOutputTest` (NoOp coverage) + `RecordingAudioOutput` test helper. |
| M2.6-C | `AudioSink` interface + `NoOpAudioSink` + `BeeperAudioSink` + `BeeperAudioSinkTest`. Modify `Pacer` to accept `audioSink: AudioSink = NoOpAudioSink` and call beforeFrame/afterFrame around runFrame. Extend `PacerTest` with the call-order assertion. |
| M2.6-D | `SpectrumIoBus` extension: ctor adds `beeper: Beeper`; `write` forwards bit 4 of ULA ports to `beeper.onWrite`. Extend `SpectrumIoBusTest` with 2 assertions. |
| M2.6-E | `SpectrumCommand` wires `Beeper(machine.cpu)`, `SpectrumIoBus(keyboard, beeper)`, `AudioOutput.tryOpen()` (or `NoOpAudioOutput` if `--no-audio`), `BeeperAudioSink`. Adds `--no-audio` flag. |
| M2.6-F | Sweep + tag `m2-phase01-6` + manual smoke (deferred to desktop). |

Within-phase deps: A → C → D (D imports Beeper from A; C imports Beeper
from A and modifies Pacer). B is independent. E depends on A+B+C+D. F
depends on all.

## Risks

- **`startBitForRender` xor-math correctness.** The `bit xor
  (events.size and 1)` trick relies on the post-render bit being the
  start bit toggled by N events. Tests #4 and #7 exercise cross-frame
  carry. Easy to get backwards; the tests pin the direction.
- **Floating-point sample-to-tstate math.** Each render call uses
  `sample.toDouble() * 69_888 / 960`. Identical Java JVM rounding
  across runs makes this deterministic. Tests pin specific sample
  indices (479, 480, 239, 240, 719, 720) — if rounding shifts, tests
  fail loudly.
- **8-bit unsigned PCM line format unavailability.** Some devices
  don't support it. `AudioOutput.tryOpen` catches `IllegalArgumentException`
  and falls back to NoOp with a WARN to stderr.
- **SourceDataLine.write blocking.** Line buffer is 4 frames (~80ms).
  Pacer's drift compensation handles brief blocking. Sustained
  blocking would indicate the audio device is severely backed up;
  user-visible as choppy sound, not a crash.
- **Phase H stuck ROM means no audio output yet.** Audio plumbing
  works; we just can't demonstrate BEEP until ROM unsticks.
- **AMP swing tuning.** AMP_HI=0xA0, AMP_LO=0x60 is a ~25% swing. If
  manual smoke later sounds wrong (too quiet/loud/harsh), tune the
  constants. Don't tune blind.
