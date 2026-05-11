# zx80-1qc: Diagnose + Fix ROM Stuck at PC=0x11E6 — Design

## Goal

Diagnose and fix the ROM-init bug that loops the Sinclair 48K ROM at
PC=0x11E6 without ever reaching the EI/HALT main loop. Acceptance gate:
`Spectrum48k().reset(); repeat(50) { runFrame() }` followed by
`mem.read(0x5C78)` returns 48..52 (the ROM's FRAMES counter at
0x5C78, incremented once per 50Hz INT after the ROM reaches its idle
loop).

Single beads task; the picker investigates AND applies the fix in one
shot. Hard stop if diagnosis can't be reached confidently.

## Context

Beads issue: `zx80-1qc`.

The M2.2 FRAMES stretch gate was the first to surface this bug. Two
prior phases ruled out plausible causes:

- **Phase H** (DD/FD passthrough coverage) — the bonus 50-frame check
  still showed FRAMES=0 after Phase H. Bug is not in opcode coverage.
- **M1 residuals** (RETI iff1 + LD A,R bump ordering) — fixed two known
  FUSE failures. The LD A,R fix in particular changes how R reads
  behave, and could plausibly affect the ROM init loop. This spec's
  **Pre-check** verifies whether residuals incidentally fixed 1qc
  before the investigation begins.

If the pre-check shows FRAMES already passing, close `zx80-1qc` and
this WU is done with no further work.

## Pre-check (do FIRST)

Before any investigation work, verify whether the M1 residuals fix
incidentally unstuck the ROM:

```kotlin
@Test
fun `PRECHECK 50 frames from real ROM increment FRAMES`() {
    val machine = Spectrum48k()
    machine.reset()
    repeat(50) { machine.runFrame() }
    assertThat(machine.mem.read(0x5C78)).isBetween(48, 52)
}
```

Add this as a temporary throwaway test in
`src/test/kotlin/ru/alepar/zx80/machine/Spectrum48kTest.kt`, run it,
capture the result, then `git checkout` to revert.

- **PASS** → close `zx80-1qc` with `--reason="incidentally fixed by M1
  residuals (LD A,R bumpR ordering); FRAMES=<observed>"`. Skip the rest
  of this WU.
- **FAIL** → proceed with investigation.

## Investigation procedure

### Step 1 — Disassemble the suspect region

Write a one-off Kotlin script (a test method, or a temp main()) that
uses the existing `Decoder` and `Dispatcher` to disassemble ROM bytes
from 0x11D0 to 0x1210. Produce a listing like:

```
0x11D0: 21 B6 5C        LD HL, 0x5CB6
0x11D3: 22 B7 5C        LD (0x5CB7), HL
...
```

Use `Op.mnemonic(OperandFetcher)` to render mnemonics. The
OperandFetcher needs to read bytes at PC+1, PC+2 etc. for multi-byte
operands.

Commit the listing to
`docs/superpowers/investigations/2026-05-10-zx80-1qc-disasm.txt`.

### Step 2 — Capture a step trace

Write a one-off test or scratch main() that:

- Constructs `Spectrum48k()`, calls `reset()`.
- Loops calling `step()` while appending per-step state to a
  StringBuilder. Per-step columns:
  `stepN, PC, opcode_byte_at_pc, A, F (hex), BC, DE, HL, SP, IX, IY,
  I, R, IFF1, IFF2, halted, tStates`.
- Stops at the FIRST of:
  - 200_000 steps,
  - `cpu.tStates >= 1_000_000`,
  - `cpu.iff1 == true` (the ROM reached EI — bug fixed itself somehow).
- Writes the trace (or just the LAST 500 lines if the file is huge) to
  `docs/superpowers/investigations/2026-05-10-zx80-1qc-trace.txt`.

The tail of the trace will show the loop cycling through a small set
of PCs.

### Step 3 — Identify the loop

Read the trace tail. Find:

- **Loop entry**: the PC where the loop body starts.
- **Loop body**: the small set of PCs that cycle.
- **Exit condition**: the conditional branch in the body that SHOULD
  exit but doesn't (e.g., a `JR NZ, dest` whose Z flag the body should
  eventually set, or a `DJNZ` whose B should eventually hit 0).
- **State pattern**: which registers and flags change per iteration.
  If they DON'T change, the loop is making no progress and the exit
  condition can never be reached.

### Step 4 — Diagnose

For each instruction in the loop body, compare:

- **Expected real-Z80 behavior** per Sean Young's TUZD §3.6 (the
  instruction reference) or the Z80 user manual.
- **Our implementation** in `src/main/kotlin/ru/alepar/zx80/op/...`.

The mismatch is the bug. Common suspects (ranked rough likelihood for
ROM-init code):

1. **DEC / INC / SUB / CP** flag handling — Z/C/H/PV/N.
2. **Compare with carry** (CP) result — should equal SUB's flag effects
   but discard the result.
3. **Conditional branch** (JR cc / JP cc / RET cc / CALL cc) — the
   condition decoding.
4. **DJNZ** — B-1, then JR NZ on the result.
5. **LD with memory side-effects** — most LD instructions don't touch
   flags, but a few ED variants do.
6. **DAA** — well-known bug-prone op.

### Step 5 — Diagnosis report

Commit a short writeup to
`docs/superpowers/investigations/2026-05-10-zx80-1qc-diagnosis.md`
covering:

- The loop body (3-10 instructions, listed with mnemonics).
- The intended exit condition.
- The state pattern on each iteration.
- The identified buggy Op + the specific flag/register/output that's
  wrong.
- The fix (1-3 lines of code).

### Step 6 — Apply the fix

- Make the smallest possible change to the buggy Op's source.
- Add a focused unit test that PINS the bug behavior: fails before the
  fix, passes after. Use TDD discipline — write the test first, see it
  fail, then apply the fix.
- Test name examples (commas, NOT colons):
  `DEC B with B=1 yields B=0 and Z=1, N=1, PV=0`.

### Step 7 — Verify the gate

Re-add the temporary throwaway test from the Pre-check (same code).
Run it. Confirm `mem.read(0x5C78) in [48, 52]`. Revert the test.

If the gate STILL fails, hard-stop conditions kick in (see below).

### Step 8 — Sweep + commit + push

Standard sweep:

1. `./gradlew clean check installDist` BUILD SUCCESSFUL.
2. New unit test passes; existing tests still green.
3. FUSE: **1356/1356** (no regression from residuals).
4. ZEXDOC: 0 IllegalStateException, 0 ERROR.
5. Programs: 5/5.
6. **50-frame FRAMES gate**: `mem.read(0x5C78) in [48, 52]`.
7. Composite SCORE: ≥ 0.998.

Optional tag: `m1-rom-init-fix` if the picker feels it warrants one;
otherwise the fix folds into the next phase.

## Hard-stop conditions

The picker STOPS and files a follow-up beads (does NOT shotgun-fix) if:

1. The disassembly is unclear — loop body has 10+ instructions or
   uses ops we have no implementation for.
2. More than one Op in the loop body looks wrong (suggests a chain of
   bugs; needs strategic decision).
3. The fix passes the per-instruction unit test but the 50-frame gate
   still fails (second bug downstream).
4. The fix breaks FUSE / ZEXDOC / programs (the fix broke something
   else; the bug is subtler than diagnosed).
5. Two consecutive Op fixes don't unstick the ROM.

On hard-stop, file `zx80-1qc-next` documenting findings so far + which
loop body PCs and Op classes were investigated.

## Artifacts committed

In `docs/superpowers/investigations/`:

- `2026-05-10-zx80-1qc-disasm.txt` — disassembly of ROM 0x11D0-0x1210.
- `2026-05-10-zx80-1qc-trace.txt` — step trace tail.
- `2026-05-10-zx80-1qc-diagnosis.md` — diagnosis report.

In `src/`:

- The buggy Op's source file (small change).
- A new unit test pinning the fix.

## Out of scope

- Building a reusable / committed step-trace facility — this is a
  one-off; tooling is throwaway scripts.
- Fixing other ROM-init bugs that haven't surfaced — gate is FRAMES
  ≈ 50; if other init bugs hide downstream, file follow-ups, don't
  shotgun.
- Phase H, M2.7 contention, M2.9 sweep — separate concerns.
- A full Spectrum ROM disassembly — only 0x11D0-0x1210 is relevant.

## Architecture

This isn't a feature spec — it's an investigation spec. No new
abstractions. Files touched depend on diagnosis. Likely candidates:

- One file in `src/main/kotlin/ru/alepar/zx80/op/*/` (the buggy Op).
- One test file in `src/test/kotlin/ru/alepar/zx80/op/*/` (new
  assertion).
- Three artifact files in `docs/superpowers/investigations/`.

## Test strategy

- **One TDD unit test** pinning the specific bug. Example shape:
  ```kotlin
  @Test
  fun `<Op> with <inputs> yields <expected outputs>`() {
      // arrange
      // act
      // assert: previously-wrong field now matches Z80 spec
  }
  ```
- **One temporary throwaway test** for the 50-frame FRAMES gate (added
  → run → reverted via `git checkout`). Same pattern as M2.2 stretch
  and Phase H bonus check.

Test names use commas/dashes — NEVER colons in backtick-quoted Kotlin
test methods.

## Validation gates

1. `./gradlew clean check installDist` BUILD SUCCESSFUL.
2. New unit test passes.
3. Existing tests still green.
4. FUSE: 1356/1356 (strict; residuals preserved).
5. ZEXDOC: clean.
6. Programs: 5/5.
7. **50-frame FRAMES gate: `mem.read(0x5C78)` in [48, 52]** — the gate.
8. Composite SCORE: ≥ 0.998.

If gate 7 fails after the fix, hard-stop conditions apply.

## Risks

- **Bug is in a "trusted" common op.** DEC, CP, JR cc are heavily
  tested by FUSE, so failure of one of these would be surprising — but
  FUSE doesn't test EVERY input combination, and a niche input (e.g.,
  `DEC B with B=1` giving PV=1 instead of PV=0) might slip through.
  Mitigation: read the trace; don't assume; verify against Sean Young.
- **Multiple bugs in a chain.** If the ROM init has N bugs and we fix
  one, it may still be stuck on the next. Hard-stop guard #5 catches
  this.
- **Trace tooling complexity.** Per-step logging at 200k steps could
  produce a multi-MB file. Saving only the tail (last 500 lines) keeps
  the artifact manageable; the loop body will be in the tail.
- **The bug might be in interrupt/IFF logic.** "iff1 never set" could
  mean the ROM never reaches the EI instruction (likely — blocked by
  loop) OR EI itself has a subtle bug (unlikely — EiTest covers it
  thoroughly). Verify EI's correctness via a trace where EI IS executed
  in a synthetic test before assuming the loop is at fault.
- **Residuals incidentally fixed it.** Pre-check covers this and shorts
  the WU. Don't skip the pre-check.
- **Time sink.** Investigation work can rabbit-hole. The hard-stop
  conditions cap the rabbit hole; if hit, file a follow-up and step
  back.
