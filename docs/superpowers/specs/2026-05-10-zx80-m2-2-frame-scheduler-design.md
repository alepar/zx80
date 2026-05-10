# M2.2: Frame Scheduler + 50Hz Interrupt — Design

## Goal

Add a 50Hz `FrameScheduler` to the M2.1 Spectrum48k machine. The
scheduler runs the CPU for one Spectrum frame (69_888 T-states) and
fires a maskable interrupt at the end. Implements the full Z80
interrupt-acknowledge sequence for IM 0/1/2 with Spectrum-bus semantics
on the data lines (bus is 0xFF), the post-EI delay slot, and
cycle-overshoot tracking so the long-run average frame is exactly
69_888 T-states.

No real-time pacing. The host calls `runFrame()` on demand; pacing
belongs in M2.4 (display backend).

## Context

Beads issue: `zx80-lxf` (parent: `zx80-48f` M2 epic).

After M2.1, `Spectrum48k.reset()` boots the real ROM and `step()`
executes one instruction at a time, but no interrupts ever fire — the
ROM's main loop (`EI; HALT; ...`) executes the HALT and stalls forever
because the maskable INT mechanism is unimplemented at the dispatcher
level. M1 has all the IFF/IM/EI/DI/RETN/RETI plumbing; what's missing
is the *acknowledge* sequence.

Spectrum frame timing: 3.5MHz CPU, 50Hz refresh → 70_000 T-states per
frame ideal; real Spectrum is 69_888 (224 lines × 312 T-states/line not
quite — actual value 69_888). M2.2 uses 69_888 exactly.

## Scope

### M2.2-A: Cpu.eiPending + Ei op

New field on `Cpu`:

```kotlin
var eiPending: Boolean = false
```

`Cpu.reset()` clears it (one extra line).

`Ei` op sets `cpu.eiPending = true` in addition to `iff1=iff2=true`.

The flag models the Z80's post-EI delay slot: maskable INTs are not
acknowledged for the instruction immediately following EI. This is
critical for the `EI; HALT` pattern in the Spectrum ROM: without the
delay, an INT could fire between EI and HALT, leaving the ROM in a
state where HALT executes but the ISR has already run.

### M2.2-B: Spectrum48k.step() handles eiPending lifecycle

Modify `Spectrum48k.step()`:

```kotlin
fun step() {
    val priorEiPending = cpu.eiPending
    val op = dispatcher.decodeAt(cpu, mem)
        ?: error("no dispatch route for opcode 0x${mem.read(cpu.pc).toString(16)} at pc=0x${cpu.pc.toString(16)}")
    op.execute(cpu, mem)
    if (priorEiPending) cpu.eiPending = false
}
```

Semantics:
- EI step: priorEiPending=false → eiPending stays true after EI runs.
- Next step: priorEiPending=true → eiPending cleared after this step.
- Subsequent steps: priorEiPending=false → no-op.

So interrupts can be acknowledged starting from the *second* instruction
after EI.

### M2.2-C: FrameScheduler class with interruptRequest()

New `machine/FrameScheduler.kt`:

```kotlin
package ru.alepar.zx80.machine

class FrameScheduler(private val machine: Spectrum48k) {

    /** Spectrum 48K: 3.5 MHz / 50 Hz = 69_888 T-states per frame. */
    companion object {
        const val T_STATES_PER_FRAME = 69_888L
    }

    /** Cycles the previous frame ran past its budget; subtracted from the next frame. */
    private var pendingCycles: Long = 0

    fun runFrame() {
        val cpu = machine.cpu
        val budget = T_STATES_PER_FRAME - pendingCycles
        val target = cpu.tStates + budget
        while (cpu.tStates < target && !cpu.halted) machine.step()
        if (cpu.halted) cpu.tStates = target  // real HW idles in HALT
        pendingCycles = cpu.tStates - target
        interruptRequest()
    }

    /**
     * Z80 maskable interrupt acknowledge. Returns true if the INT was actually taken,
     * false if it was ignored (iff1=false or in post-EI delay slot).
     */
    fun interruptRequest(): Boolean {
        val cpu = machine.cpu
        val mem = machine.mem
        if (!cpu.iff1 || cpu.eiPending) return false
        if (cpu.halted) {
            cpu.halted = false
            cpu.pc = (cpu.pc + 1) and 0xFFFF
        }
        cpu.sp = (cpu.sp - 2) and 0xFFFF
        mem.writeWord(cpu.sp, cpu.pc)
        cpu.iff1 = false
        cpu.iff2 = false
        cpu.bumpR(1)
        when (cpu.im) {
            0, 1 -> {
                cpu.pc = 0x0038
                cpu.tStates += 13
            }
            2 -> {
                val vector = ((cpu.i shl 8) or 0xFF) and 0xFFFF
                cpu.pc = mem.readWord(vector)
                cpu.tStates += 19
            }
        }
        cpu.memptr = cpu.pc
        return true
    }
}
```

### M2.2-D: runFrame() overshoot tracking

(Already shown in C — `pendingCycles` field on `FrameScheduler`.)

The math: each frame's effective budget is `T_STATES_PER_FRAME -
pendingCycles`. After the run loop, the actual cycles consumed are
`target + overshoot`; the new pendingCycles is `cpu.tStates - target`
(the overshoot). Three consecutive `runFrame()` calls accumulate
exactly `3 × 69_888 = 209_664` T-states regardless of which
instructions execute.

### M2.2-E: Spectrum48k integration

Modify `Spectrum48k`:

```kotlin
class Spectrum48k(decoder: Decoder = OpTableBuilder.build()) {
    val cpu: Cpu = Cpu()
    val mem: Memory = Memory(ReadOnlyBelow(0x4000))
    private val dispatcher = Dispatcher(decoder)
    val scheduler: FrameScheduler = FrameScheduler(this)

    fun reset() { ... unchanged ... }
    fun step() { ... with eiPending handling from M2.2-B ... }
    fun run(cycles: Long) { ... unchanged ... }
    fun runFrame() = scheduler.runFrame()
}
```

The `scheduler` field is exposed (val, not private) so tests can call
`interruptRequest()` directly without going through `runFrame()`.

### Out of scope

- NMI (M3 — Spectrum 48K hardware doesn't have an NMI source other
  than the cassette-port edge connector pin)
- Cycle-accurate INT line position within frame (M2.7 contention)
- INT line hold window (real HW asserts INT for 32 T-states during
  vertical retrace; CPU may miss it if executing a slow instruction
  with interrupts pending. We fire it at end-of-frame and assume the
  CPU is ready)
- Real-time pacing (Thread.sleep / scheduler) — M2.4 display layer
- FRAMES system-variable assertions on real ROM — M2.9 sweep

## Architecture

**File layout:**

```
src/main/kotlin/ru/alepar/zx80/
  cpu/Cpu.kt                       # add eiPending field; reset() clears
  op/misc/Ei.kt                    # set eiPending=true
  machine/FrameScheduler.kt        # NEW
  machine/Spectrum48k.kt           # eiPending in step(); scheduler field; runFrame() delegate
src/test/kotlin/ru/alepar/zx80/
  cpu/CpuResetTest.kt              # extend with eiPending assertion
  op/misc/EiTest.kt                # extend (or create) with eiPending assertion
  machine/Spectrum48kStepTest.kt   # NEW: eiPending lifecycle through step()
  machine/FrameSchedulerInterruptTest.kt  # NEW
  machine/FrameSchedulerRunFrameTest.kt   # NEW: synthetic mem
  machine/FrameSchedulerOvershootTest.kt  # NEW
  machine/Spectrum48kTest.kt       # extend: runFrame smoke (10 real-ROM frames)
```

**Data flow during one frame** (assuming ROM is running, iff1=true):

```
Spectrum48k.runFrame() → FrameScheduler.runFrame() →
  budget = 69888 - pendingCycles
  target = cpu.tStates + budget
  loop:
    machine.step()  →  dispatcher.decodeAt → op.execute → eiPending lifecycle
  (cpu.halted? jump tStates to target)
  pendingCycles = cpu.tStates - target
  interruptRequest():
    if iff1 && !eiPending:
      cpu.halted? halted=false, pc++
      push pc; clear iff1/iff2; bumpR(1)
      pc = 0x0038 (IM 1) or vector (IM 2)
      tStates += 13 or 19
      memptr = pc
```

**Why FrameScheduler is a separate class.** It owns:
- Cycle-budgeting state (`pendingCycles`)
- The Z80 INT-acknowledge sequence

Spectrum48k is a thin container of CPU+Memory+Dispatcher+IO. Frame
logic is conceptually different and will grow with M2.7 (memory
contention adds per-line interrupt windows) and possibly M3 (NMI from
edge connector). Splitting now keeps both classes focused.

## Test strategy

### Unit tests

**1. CpuResetTest extension** — 1 added assertion
- After `reset()`: `cpu.eiPending == false`.

**2. EiTest** — 1 added assertion (file already exists)
- After `Ei.execute()`: `cpu.iff1 && cpu.iff2 && cpu.eiPending`.
  Existing iff1/iff2 assertions preserved.

**3. Spectrum48kStepTest** (new) — 4 assertions
- Setup: `Spectrum48k()` (no reset — synthetic mem); `mem.loadAt(0,
  byteArrayOf(0xFB, 0x00))` (EI then NOP).
- After step 1 (EI): `cpu.eiPending == true`.
- After step 2 (NOP): `cpu.eiPending == false`.
- Bonus: after step 1, `scheduler.interruptRequest()` returns false (delay slot active).
- After step 2 (with iff1=true and a non-EI step), `scheduler.interruptRequest()` returns true.

**4. FrameSchedulerInterruptTest** (new) — 8 assertions
- `iff1=false` → `interruptRequest()` returns false; CPU state unchanged.
- `eiPending=true` → returns false; CPU state unchanged.
- IM 1, not halted, iff1=true: PC=0x1234 → after INT, PC=0x0038, SP-=2,
  mem.readWord(SP)==0x1234, iff1=iff2=false, tStates+=13, memptr=0x0038.
- IM 1, halted=true at PC=0x4321 → after INT, halted=false, PC=0x0038,
  mem.readWord(SP)==0x4322 (post-HALT address pushed).
- IM 2, I=0x39, vector address = (0x39<<8)|0xFF = 0x39FF; install
  `mem[0x39FF]=0x34` and `mem[0x3A00]=0x42` (little-endian — low byte
  first), so `readWord(0x39FF) == 0x4234`. After INT, PC=0x4234,
  tStates+=19. Note the high byte spills into the next page (0x3A00),
  so this test must use `loadAt(0x39FF, byteArrayOf(0x34, 0x42))` if
  the test wraps Spectrum48k (the bytes land in ROM area; loadAt
  bypasses the guard).
- IM 0 → behaves as IM 1 (PC=0x0038, tStates+=13). Document the
  Spectrum-bus collapse in a test comment.
- Returns true when accepted; false when ignored.

**5. FrameSchedulerRunFrameTest** (new) — 6 assertions
- Construct `Spectrum48k()`; **do not call reset()** (we don't want the
  real ROM). Instead, use `machine.mem.loadAt(0, ...)` to install the
  synthetic image (loadAt bypasses the ReadOnlyBelow guard, so we can
  drop bytes into 0x0000-0x3FFF):
  `EI` at 0x0000, `HALT` at 0x0001, padding zeros to 0x0037,
  `EI; RET` at 0x0038 (the ISR; `EI` re-enables interrupts before
  returning — Spectrum ROM does this).
- Initial state: `cpu.iff1=false, cpu.pc=0x0000, cpu.sp=0xFFFF`.
- After `runFrame()`:
  - `cpu.tStates == 69_888 + 13` (budget + IM 1 INT cycles; first frame, no overshoot
    since HALT skips tStates to budget exactly).
  - `cpu.halted == false` (INT cleared it).
  - `cpu.pc` == 0x0039 or further (we entered the ISR; specific value
    depends on how many ISR instructions executed within budget).
  - `cpu.iff1 == ?` — depends on ISR behavior; relax this assertion.
  - The first frame fired exactly 1 INT (verify via SP movement: started
    at 0xFFFF, now ≤ 0xFFFD).
- Edge case: install `JR -2` at 0x4000 (RAM, no guard issue) via
  `mem.write(0x4000, 0x18); mem.write(0x4001, 0xFE)` and set
  `cpu.pc=0x4000, cpu.iff1=true`. runFrame should iterate the JR
  (~5824 times — JR takes 12 T-states), then INT fires; final PC=0x0038
  and the JR's address (0x4000) is pushed.

**6. FrameSchedulerOvershootTest** (new) — 2 assertions
- Synthetic mem with a slow instruction (e.g., `LDIR` block move that
  takes 21 T-states per iteration). Run 3 frames.
- After frame 1: `tStates ∈ [69_888, 69_888 + 22)` (overshoot allowed).
- After frame 3: `tStates == 3 × 69_888 = 209_664` exactly. Proves
  `pendingCycles` correctly subtracts on subsequent frames.

**7. Spectrum48kTest extension — runFrame smoke** — 2 added assertions
- `Spectrum48k().reset(); repeat(10) { runFrame() }` completes without
  exception.
- After 10 frames: `cpu.tStates >= 698_880` (10 × 69_888 minimum;
  exactly = 698_880 due to overshoot tracking).

### Stretch test (optional, gate-only)

**8. Real-ROM 50-frame FRAMES system variable check.** Not in a test
file (too brittle for unit tests); covered in WU M2.2-F sweep. After
50 frames, `mem.read(0x5C78)` should equal 50 ± 1. This proves the ROM
ISR is firing and incrementing FRAMES. Failure here is a strong signal
of an INT-acknowledge bug.

About 25 new assertions across 6 new/extended test files.

## Validation gates (WU M2.2-F)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. All new tests pass (~25 new assertions).
3. Existing M1 + M2.1 tests still pass (regression check — eiPending
   field on Cpu touches a hot path; Ei op semantics changed).
4. ZEXDOC harness: 0 IllegalStateException, 0 ERROR.
5. FUSE harness: ≥ 1354/1356 (regression check — Ei op is FUSE-tested,
   though FUSE doesn't compare eiPending).
6. Programs suite: 5/5.
7. `Spectrum48k().reset(); repeat(10) { runFrame() }` — no exception,
   `cpu.tStates >= 698_880`.
8. **Stretch:** `Spectrum48k().reset(); repeat(50) { runFrame() }` then
   `mem.read(0x5C78)` ∈ [49, 51]. Demonstrates the ROM ISR is running
   and incrementing the FRAMES counter.
9. Composite SCORE ≥ 0.966 (no regression).

If gate 8 fails badly (FRAMES wildly off, or runFrame throws an
exception), triage before tagging:
- Wrong FRAMES location? Check Spectrum 48K ROM disassembly — should be
  at 0x5C78-0x5C7A (3 bytes; we read low byte).
- INT not firing? Add a debug `interruptRequest` call counter, log it.
- ISR running but PC corrupted? Step through the first ISR entry
  manually.
- Missing opcode in ISR? Check the dispatch error message in the
  exception. The 2 known FUSE residuals (`ed5f`, `ed7d`) are unlikely
  in ISR but possible.

Tag: `m2-phase01-2`.

## Work-unit breakdown

| WU | Description |
|---|---|
| M2.2-A | `Cpu.eiPending` field; `Cpu.reset()` clears it; `Ei` op sets it. Tests: extend `CpuResetTest` and `EiTest`. |
| M2.2-B | `Spectrum48k.step()` clears `eiPending` after the next instruction. Test: `Spectrum48kStepTest` (new). |
| M2.2-C | `FrameScheduler` class with `interruptRequest()` covering IM 0/1/2 + halted/eiPending/iff1=false branches. Tests: `FrameSchedulerInterruptTest` (new). |
| M2.2-D | `FrameScheduler.runFrame()` with overshoot tracking + halted-skip + ending INT. Tests: `FrameSchedulerRunFrameTest` + `FrameSchedulerOvershootTest`. |
| M2.2-E | Spectrum48k integration: `scheduler` field + `runFrame()` delegate. Test: extend `Spectrum48kTest` with the 10-frame smoke. |
| M2.2-F | Sweep + tag `m2-phase01-2`. Includes the 50-frame FRAMES check (gate 8). |

Within-phase deps: A → B (B reads the flag set by A). C is logically
independent of A/B (can be tested with eiPending=false manually) but
the run-loop integration uses both. D depends on C. E depends on D. F
depends on all. Linear order recommended.

## Risks

- **Halted-PC ordering at INT.** When INT acknowledges during HALT, PC
  must advance by 1 past HALT BEFORE being pushed. Pushing first leaves
  the return address at the HALT byte itself; `RET` from the ISR
  re-executes HALT and the system stalls. Test 4 covers both orderings.
- **eiPending lifecycle off-by-one.** The flag must be cleared *after*
  the next non-EI instruction, not after EI itself. The
  "priorEiPending capture" pattern is correct but reads a little
  subtle; reviewers should confirm the `EI; NOP; <INT can fire>` test
  covers the flag transitions explicitly.
- **bumpR(1) during INT acknowledge.** Real Z80 increments R when
  accepting an INT. We mirror this. ZEXDOC and FUSE don't test it; if
  a game reads R inside an ISR it'd see an off-by-N, but boot doesn't
  depend on it.
- **IM 2 vector at 0xFFFF.** Vector `(I shl 8) or 0xFF` could be
  0xFFFF; `Memory.readWord(0xFFFF)` already wraps to 0x0000 for the
  high byte, which is what real Z80 does. Not a risk, but worth a
  test comment.
- **Synthetic test mem vs ROM-guarded Memory.** `Spectrum48k.mem` is
  constructed with `ReadOnlyBelow(0x4000)`. Tests that need arbitrary
  bytes in the ROM area use `mem.loadAt(...)` which bypasses the
  guard. Tests that just want to put bytes in RAM (≥ 0x4000) can use
  `mem.write(...)` directly. Don't try to construct a bare `Memory()`
  and pass it to `FrameScheduler` — the scheduler takes a Spectrum48k,
  not a Memory.
- **Stretch gate 8 may fail on first try.** FRAMES location verified
  at 0x5C78 per ZX Spectrum 48K ROM disassembly references. If the
  count is exactly 0 after 50 frames, the ISR isn't running — bug in
  acknowledge. If the count is ~50 but slightly off, the ISR is fine
  but the increment race is real (low byte may be mid-update when we
  read). Either way it's good signal; budget time to interpret.
