# Phase D: Model X/Y Undocumented Flag Bits — Design

## Goal

Lift FUSE pass rate from 805/1356 (59%) to ≥90% (≥1220/1356) by modelling
the X (bit 5) and Y (bit 3) undocumented flag bits in flag-helper output.
Composite SCORE expected to climb from 0.648 to ~0.85+.

This is the **trivial / non-MEMPTR** version of Phase D. Ops whose X/Y
correctness requires MEMPTR (BIT-on-memory, IN/OUT flags) or other
quirky semantics (SCF/CCF/block ops) are explicitly out of scope and may
be filed as Phase E based on residual FUSE failures.

## Context

Z80's F register has two undocumented bits — X (0x20) and Y (0x08) —
that mirror specific source bits of various operations. ZEXDOC's CRC
tests passed cleanly for the project (zero ERROR lines across 67 test
groups) without modelling these bits, but FUSE compares the F register
exactly and so flags every X/Y mismatch. Inspection of `build/score.json`
confirms every FUSE failure observed differs from expected by some
combination of `0x20` and `0x08` only:

```
05: af mismatch: 0x0092 vs 0x00BA   (diff = 0x28)
09: af mismatch: 0x0010 vs 0x0030   (diff = 0x20)
0d: af mismatch: 0x0016 vs 0x003E   (diff = 0x28)
14: af mismatch: 0x0000 vs 0x0028   (diff = 0x28)
27: af mismatch: 0x2510 vs 0x2530   (diff = 0x20)
...
```

All sampled failures correspond to ops whose X/Y can be derived from the
result alone — no MEMPTR-tracking required.

## Scope

### Trivial result-derived X/Y (one-line addition)

For these helpers, append `f = f or (result and 0x28)` immediately
before `return AluResult(...)`:

| Helper | File line ref | X/Y source |
|---|---|---|
| `afterAdd(a, b, carry)` | `Flags.kt:35` | result |
| `afterSub(a, b, borrow)` (used by SUB and SBC) | `Flags.kt:60` | result |
| `afterAnd(a, b)` | `Flags.kt:77` | result |
| `afterOr(a, b)` | `Flags.kt:90` | result |
| `afterXor(a, b)` | `Flags.kt:103` | result |
| `afterInc(value, oldF)` | `Flags.kt:123` | result |
| `afterDec(value, oldF)` | `Flags.kt:145` | result |
| `afterCpl(a, oldF)` | `Flags.kt:229` | result |
| `afterDaa(a, oldF)` | `Flags.kt:351` | result |
| `afterRotateA(rotated, …)` | `Flags.kt:222` | rotated value |
| `computeRotateShiftFlags(result, newC)` (private; covers RLC/RRC/RL/RR/SLA/SRA/SLL/SRL) | `Flags.kt:342` | result |

### High-byte X/Y for 16-bit ops

For 16-bit pair ops, X/Y come from bits 5 and 3 of the high byte of the
result — `(result ushr 8) and 0x28`:

| Helper | File line ref |
|---|---|
| `afterAddWord(a, b, oldF)` | `Flags.kt:163` |
| `afterAdcWord(a, b, oldF)` | `Flags.kt:181` |
| `afterSbcWord(a, b, oldF)` | `Flags.kt:203` |

### Two non-trivial paths

**`CP` (compare)** must use the **operand byte** (`b`) for X/Y, not the
result. CP's flag math is otherwise identical to SUB. Currently
`AluOp.apply` routes CP through `Flags.afterSub`, which means a naive
fix would give CP the SUB-style X/Y (wrong).

Solution: add a dedicated `afterCp(a, b, borrow): AluResult` helper to
`Flags.kt`. Implementation is identical to `afterSub` except the final
F gets `f or (b and 0x28)` instead of result-derived. Update
`AluOp.apply` so `CP -> Flags.afterCp(a, b, 0)` (split off from SUB's
`afterSub`).

**`BitReg(n, src)`** computes flags inline (does not use a `Flags.afterX`
helper). Z80 spec: BIT n,r's X/Y come from the operand register being
tested. Modify `BitReg.execute` to OR `(src.read(cpu) and 0x28)` into
the new F.

### Out of scope

These ops need either MEMPTR or other special-rule modelling:

- **`BIT n,(HL)`** (`BitHl.kt`) and **`BIT n,(IX+d)`** (`BitIxd.kt`) —
  X/Y from MEMPTR_high. Without MEMPTR tracking, these cannot be
  correct. Leave at `0`.
- **`SCF`, `CCF`** — X/Y rule is `(oldA | newF) and 0x28` on Zilog NMOS;
  variant on CMOS. Tractable without MEMPTR but requires changes
  beyond a one-liner. File as Phase E if FUSE fails these post-D.
- **Block ops** `LDI/LDD/LDIR/LDDR/CPI/CPD/CPIR/CPDR` — X/Y come from
  `(A + last byte transferred)` quirks. File as Phase E if needed.
- **`IN r,(C)`** — flag X/Y from MEMPTR. Out of scope.

If FUSE still fails after Phase D and the failures are all in the above
categories, that's expected; file Phase E. If failures appear elsewhere,
investigate before declaring D complete.

## Architecture

No structural changes — all changes are additions to existing flag
helpers, plus one new helper (`afterCp`), plus one inline addition in
`BitReg`. The `X` and `Y` constants already exist in `Flags.kt:15-17`
and `Flags.kt:11-19` respectively; they're just never set today.

The work is mechanical: 11 helpers get a one-line OR; 1 new helper is
added; `AluOp.apply` updates one branch; `BitReg.execute` gets one OR.

## Test strategy

For each modified helper, add 2-3 new tests in `FlagsTest` verifying
X/Y bits match the new rule for representative inputs:
- result `0x28` → both X and Y set
- result `0x00` → neither set
- result `0x20` → only X set
- result `0x08` → only Y set

For `afterCp`: verify X/Y come from `b`, NOT result. Specifically: pick
an `a, b` such that `result = a - b` has X/Y bits *opposite* to `b`'s
bits, and assert the F bits match `b`.

For `BitReg`: verify X/Y come from the operand register.

For `afterAddWord` / `afterAdcWord` / `afterSbcWord`: verify X/Y match
`(result ushr 8) and 0x28`.

Estimated new tests: ~20.

## Validation gates (WU D-4)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. `./build/install/zx80/bin/zx80 zexdoc` still produces zero CRC errors
   (regression guard — modelling X/Y must not break ZEXDOC).
3. `./build/install/zx80/bin/zx80 score` — FUSE pass rate ≥ 90%
   (≥ 1220/1356). Stretch target ≥ 95% (≥ 1288/1356).
4. Composite score ≥ 0.85.
5. Programs suite still 5/5.

If FUSE remains < 90%, examine `build/score.json` failures. If they fall
into the documented out-of-scope categories (BIT-on-memory, SCF/CCF,
block ops, IN/OUT), file as Phase E and consider Phase D complete. If
failures show unrelated patterns, investigate.

Tag: `m1-phase-d` (no relation to `m1-cpu-complete`, which is already
applied; Phase D is post-M1 quality work).

## Work-unit breakdown

| WU | Description |
|---|---|
| D-1 | 8-bit ALU + result-derived X/Y in `afterAdd`, `afterSub`, `afterAnd`, `afterOr`, `afterXor`, `afterInc`, `afterDec`, `afterCpl`, `afterDaa`. ~10 new tests. |
| D-2 | 16-bit X/Y (`afterAddWord`, `afterAdcWord`, `afterSbcWord`) and rotate/shift X/Y (`afterRotateA`, `computeRotateShiftFlags`). ~6 new tests. |
| D-3 | Split CP from SUB: add `afterCp` helper, update `AluOp.apply`. Inline X/Y in `BitReg`. ~4 new tests. |
| D-4 | Sweep: ZEXDOC regression check, FUSE pass-rate validation, score capture, conditional Phase E filing, tag `m1-phase-d`. |

Within-phase deps: D-1 → D-2 → D-3 → D-4 (linear; D-4 verification depends on all).

## Risks

- **`afterSub` is shared between SUB, SBC, and CP today.** WU D-1 changes
  `afterSub` to OR result-X/Y. Until WU D-3 splits CP, CP will
  temporarily have the wrong X/Y. WU D-3 must land before measuring
  FUSE for CP-related tests, and the sweep WU D-4 is gated on D-3.
  Acceptable as transient state.
- **Helper-level test parity with FUSE.** New `FlagsTest` cases verify
  the rule, but the proof of correctness is the FUSE pass-rate jump in
  WU D-4. If FUSE doesn't move as expected, investigate which helper
  has a buggy update.
- **ZEXDOC regression.** Modelling X/Y could in principle change a CRC
  result if ZEXDOC's CRC includes flag bytes. Past evidence suggests it
  does not (we passed cleanly with X/Y=0). WU D-4 includes an explicit
  ZEXDOC re-run as a regression guard.
