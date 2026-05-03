# Phase G: ED Alternate Slots + X/Y Leaks — Design

## Goal

Close the 21 residual FUSE failures from Phase F by installing Z80
alternate ED-prefix slots (NEG/RETN/RETI/IM aliases) and adding result-
derived X/Y bits to the four ED ops with inline flag computation that
Phase D missed (LD A,I, LD A,R, RRD, RLD). Composite SCORE expected to
climb from 0.956 to ~0.967 with FUSE at 100%.

## Context

After Phase F, FUSE pass rate is 1335/1356 (98.4%). The 21 residuals
break down precisely (verified by grep'ing `build/score.json`):

| Group | Failing slots | Cause |
|---|---|---|
| NEG aliases | ed4c, ed54, ed5c, ed64, ed6c, ed74, ed7c | EdNop installed; should be NEG |
| RETN aliases | ed55, ed65, ed75 | EdNop installed; should be RETN |
| RETI aliases | ed5d, ed6d, ed7d | EdNop installed; should be RETI |
| IM aliases | ed4e (IM 0), ed66 (IM 0), ed76 (IM 1), ed7e (IM 2) | EdNop installed; should be IM x |
| X/Y leak | ed57 (LD A,I), ed5f (LD A,R), ed67 (RRD), ed6f (RLD) | Inline flag computation never added X/Y |

Per Z80 ISA reference, NEG has 8 documented opcode positions
(`01xxx100` pattern → 0x44, 0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C —
where xxx is "don't care"). Same for RETN (`01xx0101`), RETI
(`01xx1101`), and IM 0/1/2 with their respective patterns. Phase 2.10's
EdOps installed only the canonical slot of each (0x44 for NEG, etc.);
Phase F's `installEdNopFallback` filled the rest with EdNop, which
masked the alias behavior.

Phase G installs the alternate slots before the EdNop fallback runs, so
they take precedence.

## Scope

### G-1: ED alternate slot installation

Modify `EdOps.installInto` to install ALL Z80-standard alternate slots
for NEG, RETN, RETI, and IM. The install order is critical: alternate-
slot installs must run BEFORE `installEdNopFallback` so they aren't
overwritten.

Specific alternates per Z80 ISA:

| Op | Documented | Z80 alternate slots |
|---|---|---|
| NEG | ED 0x44 | ED 0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C (7 aliases) |
| RETN | ED 0x45 | ED 0x55, 0x65, 0x75 (3 aliases) |
| RETI | ED 0x4D | ED 0x5D, 0x6D, 0x7D (3 aliases) |
| IM 0 | ED 0x46 | ED 0x4E, 0x66, 0x6E (3 aliases) |
| IM 1 | ED 0x56 | ED 0x76 (1 alias) |
| IM 2 | ED 0x5E | ED 0x7E (1 alias) |

Total 18 alternate install positions. The Op singletons (or shared
instances) already exist from Phase 2.10; we just register them at
extra slots.

ED 0x6E is included even though FUSE doesn't seem to test it — the
Z80 ISA defines it as IM 0 alias and installing it is a one-line cost
for completeness.

### G-2: X/Y in LD A,I, LD A,R, RRD, RLD

These four ED ops compute flags inline (no `Flags.afterX` helper) and
were not updated in Phase D. Each modifies F based on a result byte:
- LD A,I: F is set from A (after A := I).
- LD A,R: F is set from A (after A := R).
- RRD: F is set from A (after the rotate).
- RLD: F is set from A (after the rotate).

For each: append `f = f or (cpu.a and 0x28)` to the inline F
computation. (Or, if the op uses a local `result` variable instead of
reading `cpu.a` after assignment, OR `(result and 0x28)`.)

### Out of scope

- Any ED slot beyond the 18 alternates listed (rare aliases like LD I,A
  variants do not exist on real Z80; the unused slots correctly stay
  EdNop).
- DD/FD-as-noop opcode coverage (Phase H).
- MEMPTR full update (FUSE doesn't compare it).

## Architecture

No structural changes. Modifications:

1. `EdOps.installInto` gains a new private method (e.g.
   `installAlternateOpcodes(d)`) called BEFORE `installEdNopFallback`.
   The method registers the 18 alternate slots from the table above
   using existing Op singletons/instances.

2. Four ED Op classes (LdAI, LdAR, Rrd, Rld) get a one-line F update
   adding result-derived X/Y.

## Test strategy

### G-1 tests

Add to `EdOpsTest.kt`:
- For each alternate slot, assert `d.ed[opcode]` is NOT `EdNop` and is
  the same instance/singleton as the canonical slot (e.g. `d.ed[0x4C]
  isSameAs d.ed[0x44]` for NEG).
- Spot-check execution: install decoder, run NEG-alias opcode at runtime,
  assert NEG behavior (A becomes 2's complement, flags set).

### G-2 tests

Add to `LdAITest`, `LdARTest`, `RrdTest`, `RldTest`:
- Single test per file verifying X/Y bits in F match the result A's
  bits 5 and 3 for representative inputs (e.g. A=0x28 → X+Y both set;
  A=0x20 → only X; A=0x08 → only Y).

About 12 new tests total.

## Validation gates (WU G-3)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. ZEXDOC regression-clean (0 IllegalStateException, 0 ERROR).
3. **FUSE = 1356/1356 (100%)** — stretch.
4. **FUSE ≥ 1352/1356 (99.7%)** — minimum acceptable.
5. Composite score ≥ 0.965.
6. Programs suite still 5/5.

If FUSE stops at 1352-1355, the residual handful is worth a quick triage
before tagging — likely a single edge case in one of the alias families.

Tag: `m1-phase-g`.

## Work-unit breakdown

| WU | Description |
|---|---|
| G-1 | EdOps alternate slot installation (NEG/RETN/RETI/IM aliases — 18 install positions). |
| G-2 | X/Y leak fix in LD A,I, LD A,R, RRD, RLD inline flag computation. |
| G-3 | Sweep + tag `m1-phase-g`. |

Within-phase deps: G-1 and G-2 are independent. G-3 depends on both.

## Risks

- **Alternate-slot install ordering.** WU G-1 must call its installer
  BEFORE `installEdNopFallback`, or EdNop wins the slot. Plan task
  assertion will catch this.
- **NEG/RETN/RETI/IM Op singletons must be re-usable.** They should be
  — they're stateless. Confirm by inspecting each and noting that
  installing the same instance at multiple slots is safe.
- **FUSE 100% is a stretch.** Some FUSE tests may have been failing for
  reasons we haven't surfaced (e.g. the harness's PR queue ordering).
  If we don't hit 1356, examine the specific residuals before tagging
  and decide whether they're worth a follow-up.
