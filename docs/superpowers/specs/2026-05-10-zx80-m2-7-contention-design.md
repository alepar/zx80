# M2.7: Memory + Port Contention — Design

## Goal

Implement Spectrum 48K memory and port contention: when the CPU
accesses contended memory (0x4000-0x7FFF) or contended ports during
the visible-frame pixel-fetch window, add the appropriate stall cycles
to `cpu.tStates`. Required for cycle-perfect game compatibility (Knight
Lore, Manic Miner, etc.). Existing M2.x tests must remain green —
contention is opt-in via a Memory / IoBus hook; default is no-op.

## Context

Beads issue: `zx80-sj5` (M2.7 placeholder).

Pre-M2.7 state: SCORE 0.998, FUSE 1356/1356, ROM boots, BASIC runnable.
Contention is the last cycle-accuracy gap. With M2.7, the emulator can
run cycle-sensitive games (loading-screen color stripes, AY-synth-via-
beeper tricks, etc).

Source of truth for the rules: Sean Young's TUZD §13 + World of
Spectrum's "Contended Memory Timing" wiki page.

### Frame timing recap

- Frame = 69_888 T-states = 312 lines × 224 T-states/line.
- Lines 0-63: top border (no contention).
- Lines 64-255: visible (192 lines).
- Lines 256-311: bottom border + vertical retrace (no contention).
- Within a visible line: T-states 0-127 = pixel-fetch (contended); 128-223 = horizontal border (no contention).

The frame T-state offset is `cpu.tStates mod 69_888`. From that:
`line = offset / 224`; `lineT = offset % 224`.

### Memory contention rules

Memory contention applies when:
1. The address is in `[0x4000, 0x7FFF]` (lower 16K RAM, where screen RAM lives).
2. The frame T-state offset is within a visible line's pixel-fetch portion: `line in 64..255` AND `lineT < 128`.

If both conditions hold, the stall cycles depend on the 8-cycle window
position within the pixel-fetch portion. With `pos = lineT % 8`, the
stall pattern is:

| pos | stall |
|---|---|
| 0 | 6 |
| 1 | 5 |
| 2 | 4 |
| 3 | 3 |
| 4 | 2 |
| 5 | 1 |
| 6 | 0 |
| 7 | 0 |

(Pattern from Sean Young TUZD §13.2.)

### Port contention rules

Port contention is more nuanced. A port access takes 4 T-states. The
rules per Sean Young §13.4 (48K):

- **High byte of port has bit 14 clear (port < 0x4000 or port >= 0xC000)** — port is in "uncontended bus address space":
  - If `A0=1` (non-ULA): no contention.
  - If `A0=0` (ULA): apply contention at T1 of the 4-cycle port access (1 check).
- **High byte has bit 14 set (port in 0x4000-0xBFFF approximately)** — "contended bus address space":
  - If `A0=1`: apply contention at T1 AND after 1 cycle, T+1 AND after another 1 cycle (3 checks at T-states 1, 2, 3 of the cycle).
  - If `A0=0`: apply contention at T1 AND T3 (2 checks).

Each "check" computes the memory contention pattern at the current
T-state (using the same `contentionAt(tStates)` function as memory)
and adds that delay.

For the implementation, simplest to model as a sequence of "subaccesses"
each adding `contentionAt(currentTStates + offset)` delay where offset
accumulates as the port cycle progresses.

### Why a hook on Memory / IoBus (not a wrapper class)

Memory is a flat 64K class with a simple read/write API. Wrapping it
would require either making it an interface (cascading changes to ~80
construction sites) or extending an open class. A `var
contentionHook: ((addr: Int) -> Unit)? = null` field is a single-line
addition with a single-line invocation per read/write. Default null =
no-op = zero performance impact when contention is off (as it is for
all existing tests).

`loadAt` is the privileged install path and does NOT invoke the hook —
ROM loading shouldn't stall the (non-existent) CPU.

## Scope

### M2.7-A: ContentionModel + memory delay

New `src/main/kotlin/ru/alepar/zx80/machine/ContentionModel.kt`:

```kotlin
package ru.alepar.zx80.machine

/**
 * Model for Spectrum memory and port contention. Production [Spectrum48kContention] implements
 * the 48K rules; tests can inject [NoContention] for deterministic timing.
 */
interface ContentionModel {
    /** Extra T-states for a memory access at [addr] while CPU is at [tStates]. */
    fun memoryDelay(tStates: Long, addr: Int): Int

    /** Extra T-states for a port access at [port] while CPU is at [tStates]. */
    fun portDelay(tStates: Long, port: Int): Int
}

/** Zero-delay model — used by tests, harness suites, and M2.x gates that assume pristine timing. */
object NoContention : ContentionModel {
    override fun memoryDelay(tStates: Long, addr: Int): Int = 0
    override fun portDelay(tStates: Long, port: Int): Int = 0
}

/**
 * ZX Spectrum 48K contention rules. Per Sean Young's TUZD §13.
 *
 * Memory contention: when addr in [0x4000, 0x7FFF] AND frame T-state in pixel-fetch window,
 * the 8-cycle pattern [6,5,4,3,2,1,0,0] applies.
 *
 * Port contention: 4-cycle port access with contention checks at specific subcycles depending
 * on (A0, A14) — see top-of-file kdoc.
 */
object Spectrum48kContention : ContentionModel {
    override fun memoryDelay(tStates: Long, addr: Int): Int {
        val masked = addr and 0xFFFF
        if (masked !in 0x4000..0x7FFF) return 0
        return contentionAt(tStates)
    }

    override fun portDelay(tStates: Long, port: Int): Int {
        val a0 = (port and 0x0001) != 0   // false=ULA, true=non-ULA
        val a14HighByte = (port and 0x4000) != 0  // true = contended bus
        var delay = 0
        var t = tStates
        when {
            !a14HighByte && !a0 -> {
                // ULA, uncontended bus: 1 check at T1
                delay += contentionAt(t)
            }
            a14HighByte && !a0 -> {
                // ULA, contended bus: checks at T1 and T3
                delay += contentionAt(t); t += 1 + delay
                delay += contentionAt(t + 2)
            }
            a14HighByte && a0 -> {
                // Non-ULA, contended bus: checks at T1, T2, T3 (3 single-cycle checks)
                for (i in 0..2) {
                    val d = contentionAt(t)
                    delay += d
                    t += 1 + d
                }
            }
            // !a14HighByte && a0: no contention
        }
        return delay
    }

    /** 8-cycle pattern within the pixel-fetch portion of a visible scanline. */
    private fun contentionAt(tStates: Long): Int {
        val frameT = (tStates % FRAME_T_STATES).toInt()
        val line = frameT / LINE_T_STATES
        if (line < FIRST_VISIBLE_LINE || line > LAST_VISIBLE_LINE) return 0
        val lineT = frameT % LINE_T_STATES
        if (lineT >= PIXEL_FETCH_END) return 0
        return PATTERN[lineT % 8]
    }

    private val PATTERN = intArrayOf(6, 5, 4, 3, 2, 1, 0, 0)
    private const val FRAME_T_STATES = 69_888L
    private const val LINE_T_STATES = 224
    private const val FIRST_VISIBLE_LINE = 64
    private const val LAST_VISIBLE_LINE = 255
    private const val PIXEL_FETCH_END = 128
}
```

**ContentionModelTest** — about 15 assertions:

Memory rules:
- Memory access outside contended range (ROM, upper RAM): 0 delay.
- Memory access during top border (line 0-63): 0 delay.
- Memory access during bottom border (line 256+): 0 delay.
- Memory access during horizontal border (lineT >= 128): 0 delay.
- Pattern verification: 8 successive T-states at start of contention → 6, 5, 4, 3, 2, 1, 0, 0.
- Wrap-around: tStates beyond one frame still gives correct frame T-state offset.

Port rules:
- Port 0xFE (A0=0, high byte=0xFF → a14=1): contended, 2 checks (T1 + T3).
- Port 0x7FFE (A0=0, high byte=0x7F → a14=1): contended, 2 checks.
- Port 0xFFFE (A0=0, high byte=0xFF → a14=1): contended, 2 checks.
- Port 0x00FE (A0=0, high byte=0x00 → a14=0): ULA but uncontended bus, 1 check.
- Port 0x00FF (A0=1, high byte=0x00 → a14=0): no contention, 0 delay.
- Port 0x4001 (A0=1, high byte=0x40 → a14=1): non-ULA contended bus, 3 checks.

### M2.7-B: Memory hook

Modify `src/main/kotlin/ru/alepar/zx80/cpu/Memory.kt`:

```kotlin
class Memory(private val writePolicy: WritePolicy = OpenPolicy) {
    private val bytes = ByteArray(SIZE)

    /**
     * Invoked before each `read` / `write` (NOT during `loadAt`). Production wiring in
     * SpectrumCommand passes a lambda that calls `cpu.tStates += contention.memoryDelay(...)`.
     * Default null = no-op; existing tests untouched.
     */
    var contentionHook: ((addr: Int) -> Unit)? = null

    fun read(addr: Int): Int {
        contentionHook?.invoke(addr)
        return bytes[addr and ADDR_MASK].toInt() and 0xFF
    }

    fun write(addr: Int, value: Int) {
        if (!writePolicy.shouldWrite(addr and ADDR_MASK)) return
        contentionHook?.invoke(addr)
        bytes[addr and ADDR_MASK] = (value and 0xFF).toByte()
    }

    fun readWord(addr: Int): Int = read(addr) or (read(addr + 1) shl 8)

    fun writeWord(addr: Int, value: Int) {
        write(addr, value and 0xFF)
        write(addr + 1, (value ushr 8) and 0xFF)
    }

    fun loadAt(addr: Int, payload: ByteArray) {
        // unchanged — privileged install path, no hook invocation
    }
    // ... companion unchanged ...
}
```

**MemoryContentionTest** — 4 assertions:
- Default `Memory()` has no contention hook; reads/writes don't invoke anything.
- With a hook installed: `read(0x5000)` invokes the hook with addr=0x5000.
- With a hook installed: `write(0x5000, 0x42)` invokes the hook BEFORE writing.
- `loadAt(0, ...)` does NOT invoke the hook.

### M2.7-C: SpectrumIoBus hook

Modify `src/main/kotlin/ru/alepar/zx80/machine/SpectrumIoBus.kt`:

```kotlin
class SpectrumIoBus(
    private val keyboard: Keyboard,
    private val beeper: Beeper,
    private val border: BorderState,
) : IoBus {
    /** Invoked before each `read` / `write`. SpectrumCommand wires this to apply port contention. */
    var contentionHook: ((port: Int) -> Unit)? = null

    override fun read(port: Int): Int {
        contentionHook?.invoke(port)
        // ... existing logic unchanged
    }

    override fun write(port: Int, value: Int) {
        contentionHook?.invoke(port)
        // ... existing logic unchanged
    }
}
```

**SpectrumIoBusContentionTest** — 3 assertions:
- Default bus has no contention hook; reads/writes proceed normally.
- With hook: `read(0xFE)` invokes hook with port=0xFE.
- With hook: `write(0xFE, 0x07)` invokes hook BEFORE writing border state.

### M2.7-D: SpectrumCommand wiring + --no-contention flag

Modify `src/main/kotlin/ru/alepar/zx80/cli/SpectrumCommand.kt`:

```kotlin
class SpectrumCommand : CliktCommand(name = "spectrum") {
    private val scale by option("--scale").int().default(2)
    private val noAudio by option("--no-audio").flag()
    private val noContention by option("--no-contention", help = "Disable memory/port contention").flag()

    override fun run() {
        val machine = Spectrum48k()
        val keyboard = Keyboard()
        val beeper = Beeper(machine.cpu)
        val border = BorderState()
        val bus = SpectrumIoBus(keyboard, beeper, border)
        machine.cpu.io = bus

        val contention: ContentionModel = if (noContention) NoContention else Spectrum48kContention
        machine.mem.contentionHook = { addr ->
            machine.cpu.tStates += contention.memoryDelay(machine.cpu.tStates, addr)
        }
        bus.contentionHook = { port ->
            machine.cpu.tStates += contention.portDelay(machine.cpu.tStates, port)
        }

        machine.reset()
        val audioOut = if (noAudio) NoOpAudioOutput else AudioOutput.tryOpen()
        val audioSink = BeeperAudioSink(beeper, audioOut)
        val renderer = BorderedUlaRenderer(UlaRenderer(), border)
        val pacer = Pacer(machine, renderer, audioSink = audioSink)
        val window = SpectrumWindow(pacer, keyboard, scale)
        window.show()
    }
}
```

Hooks must be installed BEFORE `reset()` so the boot sequence experiences
contention. (Though reset itself doesn't access memory besides `loadAt`,
which bypasses the hook.)

### M2.7-E: Sweep + tag

Standard sweep:
1. `./gradlew clean check installDist` BUILD SUCCESSFUL.
2. New tests pass.
3. Existing tests still pass — contention is opt-in via hook; default no-op preserves prior behavior.
4. FUSE: 1356/1356 (FUSE constructs bare Memory, no hook).
5. ZEXDOC: clean.
6. Programs: 5/5.
7. BootsToBasic (from M2.9): 3/3 PASS — verify by running with contention enabled. The ROM's boot timing extends slightly with contention but stays within RUN_FRAMES=500 budget.
8. Composite SCORE: ≥ 0.998.

Tag: `m2-phase01-7`.

### Out of scope

- 128K Spectrum's different contention timing (M3 / future 128K spec).
- Floating bus reads (advanced quirk — few games depend on it).
- "Snow effect" (R register pointing into contended ROM — extremely niche).
- Audio jitter from contention (Pacer's drift compensation absorbs it).
- Per-game contention-sensitivity regression tests (deferred — would need real-game .tap files in M3).
- Contention applied during interrupt acknowledge (refinement; M3 may revisit).

## Architecture

```
src/main/kotlin/ru/alepar/zx80/
  machine/ContentionModel.kt       NEW
  cpu/Memory.kt                    MODIFY (add contentionHook field)
  machine/SpectrumIoBus.kt         MODIFY (add contentionHook field)
  cli/SpectrumCommand.kt           MODIFY (wire both hooks + --no-contention flag)
src/test/kotlin/ru/alepar/zx80/
  machine/ContentionModelTest.kt           NEW (~15 assertions)
  cpu/MemoryContentionTest.kt              NEW (~4 assertions)
  machine/SpectrumIoBusContentionTest.kt   NEW (~3 assertions)
```

**Threading.** Hooks are invoked on the Pacer thread (during runFrame's
Op.execute). No locks.

**Determinism.** Contention is a deterministic function of `tStates`
and `addr/port`. Same inputs → same delays.

**Test isolation.** Tests that don't install hooks see the original
M2.1-M2.6 behavior. The BootsToBasic and HELLO WORLD integration
tests can either install contention (more realistic) or leave it off
(deterministic for the gate). Pick deterministic for the gate;
production uses contention.

## Test strategy

### ContentionModelTest — ~15 assertions

```kotlin
@Test fun `memoryDelay returns 0 for ROM address`() {
    assertThat(Spectrum48kContention.memoryDelay(20000L, 0x0100)).isEqualTo(0)
}

@Test fun `memoryDelay returns 0 during top border (line 0-63)`() {
    assertThat(Spectrum48kContention.memoryDelay(100L, 0x5000)).isEqualTo(0)  // line 0
    assertThat(Spectrum48kContention.memoryDelay(14000L, 0x5000)).isEqualTo(0) // line 62
}

@Test fun `memoryDelay returns 0 during bottom border (line 256+)`() {
    assertThat(Spectrum48kContention.memoryDelay(60000L, 0x5000)).isEqualTo(0) // line 267
}

@Test fun `memoryDelay returns 0 during horizontal border (lineT >= 128)`() {
    // line 64, lineT 128: frame T = 64*224 + 128 = 14464
    assertThat(Spectrum48kContention.memoryDelay(14464L, 0x5000)).isEqualTo(0)
}

@Test fun `memoryDelay 8-cycle pattern at start of contention`() {
    // line 64, lineT 0: frame T = 14336
    val base = 14336L
    assertThat(Spectrum48kContention.memoryDelay(base + 0, 0x5000)).isEqualTo(6)
    assertThat(Spectrum48kContention.memoryDelay(base + 1, 0x5000)).isEqualTo(5)
    assertThat(Spectrum48kContention.memoryDelay(base + 2, 0x5000)).isEqualTo(4)
    assertThat(Spectrum48kContention.memoryDelay(base + 3, 0x5000)).isEqualTo(3)
    assertThat(Spectrum48kContention.memoryDelay(base + 4, 0x5000)).isEqualTo(2)
    assertThat(Spectrum48kContention.memoryDelay(base + 5, 0x5000)).isEqualTo(1)
    assertThat(Spectrum48kContention.memoryDelay(base + 6, 0x5000)).isEqualTo(0)
    assertThat(Spectrum48kContention.memoryDelay(base + 7, 0x5000)).isEqualTo(0)
}

@Test fun `memoryDelay wraps around multi-frame T-states`() {
    // T = FRAME + 14336 should behave same as T = 14336
    assertThat(Spectrum48kContention.memoryDelay(69_888L + 14336L, 0x5000)).isEqualTo(6)
}

@Test fun `portDelay ULA port 0xFE in uncontended bus during contention window`() {
    // High byte 0x00 → A14=0; A0=0 → ULA. Single check at T1.
    val t = 14336L  // contention pattern offset 0 = 6 stall
    assertThat(Spectrum48kContention.portDelay(t, 0x00FE)).isEqualTo(6)
}

@Test fun `portDelay ULA port 0xFEFE in contended bus`() {
    // High byte 0xFE → A14=1; A0=0 → ULA. Two checks (T1 + T3).
    val t = 14336L
    val firstCheck = 6
    // After first check, T advances by 1 + 6 = 7; then +2 more for T3 = 9 offset.
    // contentionAt(14336+9) = pattern[(0+9)%8] = pattern[1] = 5
    assertThat(Spectrum48kContention.portDelay(t, 0xFEFE)).isEqualTo(firstCheck + 5)
}

@Test fun `portDelay non-ULA non-contended port has zero delay`() {
    assertThat(Spectrum48kContention.portDelay(14336L, 0x00FF)).isEqualTo(0)
}

@Test fun `portDelay non-ULA contended port has 3 checks`() {
    // High byte 0x40 → A14=1; A0=1 → non-ULA. Three checks.
    // contentionAt(14336) = 6; t+=7; contentionAt(14343) = pattern[7%8]=0; t+=1+0=8;
    // contentionAt(14344) = pattern[8%8]=6
    // Total: 6+0+6 = 12
    assertThat(Spectrum48kContention.portDelay(14336L, 0x4001)).isEqualTo(12)
}

@Test fun `NoContention returns zero for everything`() {
    assertThat(NoContention.memoryDelay(0L, 0x5000)).isEqualTo(0)
    assertThat(NoContention.memoryDelay(14336L, 0x5000)).isEqualTo(0)
    assertThat(NoContention.portDelay(14336L, 0xFEFE)).isEqualTo(0)
}
```

(~15 assertions; test names use commas, not colons.)

### MemoryContentionTest — 4 assertions

```kotlin
@Test fun `default Memory has no contentionHook and reads succeed`() {
    val mem = Memory()
    assertThat(mem.contentionHook).isNull()
    assertThat(mem.read(0x5000)).isEqualTo(0)
}

@Test fun `installed contentionHook is invoked on read with the address`() {
    val mem = Memory()
    var observed: Int = -1
    mem.contentionHook = { addr -> observed = addr }
    mem.read(0x5000)
    assertThat(observed).isEqualTo(0x5000)
}

@Test fun `installed contentionHook is invoked on write before storing`() {
    val mem = Memory()
    var observed: Int = -1
    mem.contentionHook = { addr -> observed = addr }
    mem.write(0x4000, 0x42)
    assertThat(observed).isEqualTo(0x4000)
    assertThat(mem.read(0x4000)).isEqualTo(0x42)
}

@Test fun `loadAt does NOT invoke contentionHook`() {
    val mem = Memory()
    var invoked = false
    mem.contentionHook = { invoked = true }
    mem.loadAt(0, byteArrayOf(0x11, 0x22))
    assertThat(invoked).isFalse
}
```

### SpectrumIoBusContentionTest — 3 assertions

Test that the hook fires on read and write, and that without a hook
the existing behavior is unchanged.

About 22 new assertions total.

## Validation gates (M2.7-F)

1. `./gradlew clean check installDist` BUILD SUCCESSFUL.
2. All new tests pass (~22 assertions).
3. Existing tests still pass — contention is opt-in.
4. FUSE: 1356/1356 (FUSE harness constructs bare Memory, no hook).
5. ZEXDOC: clean.
6. Programs: 5/5.
7. **BootsToBasic with contention enabled**: 3/3 PASS, verify via score harness. The ROM boot timing extends but stays within RUN_FRAMES=500.
8. **HELLO WORLD test** (if landed): still PASS with contention enabled. Adjust SpectrumTypist's frame budgets if needed.
9. Composite SCORE: ≥ 0.998.

Tag: `m2-phase01-7`.

## Risks

- **Off-by-one at contention window edges.** T=14335 vs T=14336 — common emulator bug. Tests pin both.
- **Port contention 3-check sequence math.** Each sub-check advances T by `1 + delay`. Easy to mis-accumulate. Tests pin concrete (port, T) → delay tuples.
- **BootsToBasic re-tuning.** Adding contention extends boot time. The current RUN_FRAMES=500 has ~150-frame headroom past screen-draw at ~350; contention adds at most ~30% to memory-access cycles, which extends boot from ~82 frames to ~107. Still within budget. If gates fail, bump RUN_FRAMES.
- **HELLO WORLD timing shifts.** SpectrumTypist's 5/5 framesHeld/Released may need a small bump if the ROM's keyboard ISR runs slightly later due to contention. Mitigated by the contention being applied per-access, not in bulk — keyboard scan loop accesses are bounded.
- **CPU performance.** One nullable function invocation per memory/port access in the production path. JVM inlines this. Negligible.
- **Test name colons.** Recurring author trap. All test names use commas/spaces.
