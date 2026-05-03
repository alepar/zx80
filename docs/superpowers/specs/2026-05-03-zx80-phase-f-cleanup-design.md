# Phase F: Comprehensive Cleanup — Design

## Goal

Lift FUSE pass rate from 1195/1356 (88.1%) to ≥99% (≥1340/1356) by closing
the remaining gaps identified after Phase E. Composite SCORE expected to
climb from 0.849 to ≥0.92.

## Context

After Phase E, 161 FUSE failures remain. Per the Phase E subagent's
analysis, all are in three independent categories — none requires further
flag-rule research:

| Category | Count | Cause |
|---|---|---|
| Unmapped DDCB/FDCB BIT mirror, ED unmapped, DD/FD slots | ~132 | Opcode coverage gaps |
| FUSE port-read mismatches (IN r,(C), INI/IND/etc., IN A,(n)) | ~28 | Harness IoBus returns 0xFF instead of FUSE-specified bytes |
| HALT PC behavior | 1 | Z80 leaves PC at HALT byte during halted; we advance past |

Each category is independently addressable. After Phase F, the FUSE suite
is essentially fully passing.

## Scope

### F-A: Opcode coverage (~132 cases)

Three sub-fixes, all in one WU:

**DDCB/FDCB BIT mirror slots.** Phase 2.13 deliberately left rrr ∈ {0..5,7}
slots null in the BIT block (CB 0x40-0x7F under DD/FD prefix) because
ZEXDOC didn't need them. FUSE does. Z80 quirk: BIT n,r,(IX+d) at the mirror
slots behaves *identically* to the documented `BIT n, (IX+d)` form (BIT has
no result; the `r` register field is ignored). So we install the same
`BitIxd(idx, n)` instance at all 8 rrr slots per (n, prefix), not just
rrr=6.

Modify `IxCbOps.installBit`:

```kotlin
private fun installBit(table: Array<Op?>, idx: IndexReg) {
    for (n in 0..7) {
        val opInstance = BitIxd(idx, n)
        for (rrrBits in 0..7) {
            val opcode = 0x40 or (n shl 3) or rrrBits
            table[opcode] = opInstance  // same instance at all rrr — undocumented mirror
        }
    }
}
```

That changes 16 install positions to 128 (~+112 slots filled per the
project-wide table, since both DD and FD prefixes get this).

**ED unmapped slots.** Real Z80 treats ED 00-3F, ED 80-9F (excluding the
block ops we've installed), ED C0-FF as 8-cycle NOPs that advance PC by 2.
We currently leave them null, causing dispatch crashes for tests that hit
those slots. Add `EdNop` object:

```kotlin
object EdNop : Op {
    override val operandLength = 0
    override val baseCycles = 8
    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }
    override fun mnemonic(operands: OperandFetcher) = "NOP*"
}
```

Then in `EdOps.installInto`, after all real installs, fill any
still-null `d.ed[i]` for i in 0..255 with `EdNop`.

**DD/FD remaining gaps.** Two specific FUSE-failing slots dispatch to null
under DD/FD. Subagent's report didn't enumerate which two. F-A's
verification will surface them; install per Z80 spec (likely DD/FD on a
non-half opcode that should behave as the unprefixed op + 4 T-states +
pc+=2 + bumpR(2)). If the two slots turn out to need a more invasive
DD/FD-as-noop handler, scope to a follow-up.

### F-B: FUSE-aware IoBus (~28 cases)

FUSE's input format includes "PR" (port read) events between the
register-state line and the memory blocks, looking like:

```
04 PR 1234 56
```

Where: 04 = T-state offset, PR = "port read", 1234 = port address,
56 = byte value the port returns. Our current `FuseTestParser` skips event
lines (the parser's docstring says "lines starting with whitespace that we
ignore"). Need to change.

Three changes:

1. **Extend `FuseTestParser`** to capture PR events from input cases.
   Adds a `portReads: List<PortRead>` field on `FuseInputCase` where
   `PortRead(tStateOffset: Int, port: Int, byte: Int)`. Other event types
   (PW for port write, MR/MW for memory) can be ignored or also parsed if
   useful.

2. **New `QueueIoBus`** in `src/main/kotlin/ru/alepar/zx80/zexdoc/` (or a
   new `src/main/kotlin/ru/alepar/zx80/harness/io/`):
   - Constructed with `List<PortRead>`.
   - `read(port: Int): Int` pops the front of the queue, asserts the port
     matches expected (or just returns the byte ignoring port — FUSE's PR
     events list port for documentation, but in practice Z80 reads come
     from the port specified by the IN op, so pop-and-return is fine).
   - `write(port: Int, byte: Int)` is a no-op for FUSE (or buffers PWs if
     we want to validate them later — out of scope).

3. **Wire into `FuseSuite.runOne`** — replace the default IoBus on `cpu.io`
   with a `QueueIoBus(input.portReads)`.

This is purely harness work. CPU code is untouched.

### F-C: HALT PC fix (1 case)

Z80 hardware behavior: HALT executes by repeatedly fetching itself until
an interrupt or NMI occurs. PC stays at the HALT byte during halted state.
On INT-acknowledge, the interrupt mechanism advances PC by 1 to skip the
HALT.

Current `src/main/kotlin/ru/alepar/zx80/op/misc/Halt.kt` advances PC by 1
on first HALT execution. The fix: **don't advance PC on HALT**. The
existing `cpu.halted = true` flag prevents the dispatcher from re-running
HALT every cycle (the run loop checks `!cpu.halted`).

Change `Halt.execute`:

```kotlin
override fun execute(cpu: Cpu, mem: Memory) {
    cpu.halted = true
    cpu.bumpR()
    cpu.tStates += baseCycles
    // PC NOT advanced — Z80 hardware leaves PC at HALT byte
}
```

Also update interrupt-handling code (in CPU run loops or wherever INT
acknowledge happens) to advance PC past HALT when handling an interrupt.
For M1 we don't have interrupts firing, so this advance is theoretical;
just adding the comment is fine.

### Out of scope

- **DD/FD-as-noop on all 256 main slots.** ZEXDOC and FUSE don't exercise
  every such combination; filling all of them is a separate "opcode coverage
  to 100%" phase. After Phase F, opcodes-passed will still be ~1188+~140 ≈
  1330 / 1792 ≈ 74%. Pushing to 100% is Phase G.
- **MEMPTR full update across all 30 memory-touching ops.** Not needed for
  FUSE (which doesn't compare MEMPTR). Real-game emulation may require it
  later.

## Architecture

No structural changes. New types:

- `EdNop` — singleton Op object (one new file).
- `PortRead` data class — added to `FuseTestParser.kt`.
- `QueueIoBus` — new class (one new file).

Modified files:
- `src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt` — installBit fills 8 rrr
  slots per (n, prefix).
- `src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt` — fill remaining null slots
  with `EdNop`.
- `src/main/kotlin/ru/alepar/zx80/op/misc/Halt.kt` — don't advance PC.
- `src/main/kotlin/ru/alepar/zx80/harness/fuse/FuseTestParser.kt` — parse PR
  events.
- `src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt` — install
  QueueIoBus on cpu.io.

## Test strategy

- **F-1 (opcode coverage):** add `IxCbOpsTest` cases asserting
  `d.ddcb[0x40]` (BIT 0,B,(IX+d) — undocumented mirror) is the same instance as
  `d.ddcb[0x46]` (or at least a `BitIxd` instance). Add `EdOpsTest`
  cases asserting `EdNop` is installed at representative null slots
  (e.g. ED 0x00, ED 0xFF). Add an `EdNopTest`.
- **F-2 (IoBus):** unit tests for `QueueIoBus` (returns sequenced bytes;
  asserts/handles empty queue). Unit test extending `FuseTestParserTest`
  for PR-event parsing.
- **F-3 (HALT):** unit test in `HaltTest` asserting PC is unchanged after
  execute. Adjust any existing test that asserts PC advanced.
- **F-4 (sweep):** ZEXDOC regression check + FUSE delta + tag.

About 15 new tests.

## Validation gates (WU F-4)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. ZEXDOC regression check — still 0 IllegalStateException, 0 ERROR.
3. FUSE pass rate ≥ 1340/1356 (≥98.8%). Stretch ≥1356/1356 (100%).
4. Composite score ≥ 0.92.
5. Programs suite still 5/5.

If FUSE remains <98%, examine residuals. Likely candidates: a specific
DDCB BIT pattern we didn't cover correctly, or a PR event format detail
the parser missed. Triage and fix in WU F-4.

Tag: `m1-phase-f`.

## Work-unit breakdown

| WU | Description |
|---|---|
| F-1 | DDCB/FDCB BIT mirror + ED unmapped (`EdNop`) + investigate 2 DD/FD slots. |
| F-2 | FUSE-aware IoBus: parser PR-event extension + `QueueIoBus` + `FuseSuite` wiring. |
| F-3 | HALT PC fix. |
| F-4 | Sweep + tag `m1-phase-f`. |

Within-phase deps: F-1, F-2, F-3 are independent. F-4 depends on all three.

## Risks

- **PR event format edge cases.** FUSE's event format may have subtleties
  (e.g. PR events that fire during prefix bytes, or multiple PR events per
  test). Plan WU F-2 starts with the simplest case (one PR per test) and
  iterates if FUSE failures persist.
- **HALT PC fix breaking existing tests.** Multiple tests likely assert the
  current "advance PC by 1" behavior. WU F-3 must adjust all of them.
- **DD/FD 2-slot mystery.** Spec couldn't enumerate exactly which two
  failing slots they are. WU F-1 surface-test will reveal them; if scope
  grows beyond a 2-line fix, separate them into a follow-up issue.
