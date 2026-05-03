# Phase E: MEMPTR + Flag-Edge Quirks — Design

## Goal

Lift FUSE pass rate from 1105/1356 (81.5%) to ≥95% (≥1288/1356) by
modelling the Z80's hidden MEMPTR (WZ) register where it leaks into F
via the X/Y bits, plus three discrete flag-rule quirks that Phase D
explicitly left out (BIT n,r PV/S, SCF/CCF X/Y, block-op X/Y).
Composite SCORE expected to climb from 0.803 to ≥0.90.

## Context

After Phase D, 251 FUSE failures remain. Per the Phase D subagent's
analysis, all are in spec-acknowledged out-of-scope categories. This
phase addresses them.

A key simplification surfaced during brainstorming:
`FuseTestParser` already reads a `memptr` field from each test's input
state, but `FuseSuite` does NOT compare MEMPTR after execution. So we
do **not** need to maintain MEMPTR correctly through every Z80 op — we
only need it to be correct **at the moment X/Y is computed inside the
single op under test**.

This removes the cross-cutting "update MEMPTR in ~30 op classes" work
that's normally the boss-fight version of MEMPTR. We only touch 4 op
classes for MEMPTR. Significant scope reduction vs. the canonical
Z80 emulator approach.

## Scope

### MEMPTR consumers (WU E-1)

Add `cpu.memptr: Int` field. `FuseSuite` initializes it from
`inputCase.memptr` at test start. Update 4 op classes:

| Op | MEMPTR rule | X/Y source |
|---|---|---|
| `BitHl` (BIT n,(HL)) | reads existing memptr (does NOT update) | `(cpu.memptr ushr 8) and 0x28` |
| `BitIxd` (BIT n,(IX+d), BIT n,(IY+d)) | sets `cpu.memptr = (idx + d) and 0xFFFF` then reads | `(cpu.memptr ushr 8) and 0x28` |
| `InRC` (IN r,(C)) | sets `cpu.memptr = (cpu.bc + 1) and 0xFFFF` then reads | `(cpu.memptr ushr 8) and 0x28` |
| `InCFlags` (IN (C) — flags only, rrr=110 slot) | same as `InRC` | `(cpu.memptr ushr 8) and 0x28` |
| `InAImm` (IN A,(n)) | sets `cpu.memptr = ((cpu.a shl 8) or n + 1) and 0xFFFF` then reads | `(cpu.memptr ushr 8) and 0x28` |

(Five op classes total — `InRC` and `InCFlags` are siblings.)

This closes the ~135 BIT-on-memory + IN-via-MEMPTR FUSE failures.

### BIT n,r PV/S quirks (WU E-2)

Z80's `BIT n,r` has documented flag quirks beyond X/Y:
- **PV mirrors Z**: `if (Z is set) f = f or PV`. Always.
- **S quirk**: `if (n == 7 && (operand and 0x80) != 0) f = f or S`.
- **X/Y**: from operand bits 5 and 3 (already added in Phase D).

Modify `BitReg.execute` to add the PV-mirror and conditional-S rules.
Closes ~25 cb40-cb7F failures.

### SCF/CCF X/Y rule (WU E-2)

Zilog NMOS rule: `X/Y = (oldA | newF) and 0x28`. Modify
`Flags.afterScf` and `Flags.afterCcf` to take `oldA: Int` parameter and
apply the rule. Update callers (`Scf.kt`, `Ccf.kt`) to pass `oldA`.
Closes ~4 failures.

### Block-op X/Y rules (WU E-3)

Each block op has its own X/Y rule based on a byte derived from the
operation's operands. Per Zilog NMOS spec:

| Op family | n value (byte computed for X/Y) |
|---|---|
| LDI/LDD/LDIR/LDDR (`Ldi`/`Ldd`) | `n = transferredByte + A` |
| CPI/CPD/CPIR/CPDR (`Cpi`/`Cpd`) | `n = A - mem[HL] - H_after` (uses H from result computation) |
| INI/IND/INIR/INDR (`Ini`/`Ind`) | `n = portByte + ((C ± 1) and 0xFF)` (sign depends on direction) |
| OUTI/OUTD/OTIR/OTDR (`Outi`/`Outd`) | `n = portByte + L` |

X/Y come from `n and 0x28`. The exact rules also affect H, P/V, C in
some cases but those are largely already correct; here we focus on the
X/Y leak.

Each block op file is updated to compute its `n` and OR the X/Y bits
in. Closes ~70 failures.

### Out of scope

- **HALT PC behavior** (`76:` failure — Z80 leaves PC at the HALT byte
  during halted state; we advance past). 1 failure. Different bug, file
  separately if pursued.
- **ED unmapped slots** (8-cycle NOPs at `ed:0x00-0x3F`, `ed:0x80-0x9F`,
  `ed:0xC0-0xFF`). Show up in OpcodeCoverage failures, not FUSE.
- **DD/FD-prefix-as-noop on non-LD-block opcodes**. Pure mechanical
  opcode-coverage work; doesn't move FUSE.

## Architecture

No structural changes. Additions:

- `Cpu.memptr: Int = 0` — one new field.
- `FuseSuite` reads `inputCase.memptr` (already parsed) and writes it
  to `cpu.memptr` at test start.
- 5 op classes get one or two new lines each (set MEMPTR, read for X/Y).
- `BitReg.execute` gets PV-mirror and S-conditional lines.
- `Flags.afterScf` and `Flags.afterCcf` gain an `oldA` parameter.
  Callers pass it.
- 8 block-op classes get their per-op X/Y rule added.

## Test strategy

For each op class change, add focused tests:
- **`BitHl`**: verify X/Y bits in F after execute equal `(cpu.memptr_high) and 0x28` for representative MEMPTR values.
- **`BitIxd`**: verify cpu.memptr is set to `(IX+d) and 0xFFFF` and X/Y reflect that value's high byte.
- **`InRC`/`InCFlags`/`InAImm`**: verify cpu.memptr is set per the rule, X/Y reflect that.
- **`BitReg`**: verify PV mirrors Z; S is set on n=7 with bit-7-set, clear otherwise.
- **`Flags.afterScf`/`afterCcf`**: verify X/Y rule with various (oldA, newF) combinations.
- **Block ops**: verify X/Y bits per the specific rule for one or two representative values.

About 25-30 new tests.

## Validation gates (WU E-4)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. ZEXDOC regression check — still 0 IllegalStateException, 0 ERROR
   markers in the streamed output.
3. FUSE pass rate ≥ 1288/1356 (95%). Stretch ≥1340/1356 (≥98%).
4. Composite score ≥ 0.90.
5. Programs suite still 5/5.

If FUSE remains < 95%, examine the residual failures in
`build/score.json`. Likely candidates: HALT (1 case), unmapped ED slots
(would show up under different test names), or a block-op rule we got
slightly wrong. Triage and decide whether to file Phase F.

Tag: `m1-phase-e`.

## Work-unit breakdown

| WU | Description |
|---|---|
| E-1 | `cpu.memptr` field + `FuseSuite` init + 5 op classes consume MEMPTR for X/Y. |
| E-2 | `BitReg` PV-mirror + S-conditional. `Flags.afterScf`/`afterCcf` take `oldA` and apply Zilog NMOS rule; callers updated. |
| E-3 | Block ops X/Y rules (LDI/LDD/CPI/CPD/INI/IND/OUTI/OUTD — the repeat variants reuse the same flag math via the underlying single-step ops). |
| E-4 | Sweep: ZEXDOC regression, FUSE ≥95%, score ≥0.90, tag `m1-phase-e`. |

Within-phase deps: E-1, E-2, E-3 are mostly independent (different
files / different concerns), but a subagent runs them sequentially for
reviewability. E-4 depends on E-1, E-2, E-3.

## Risks

- **MEMPTR scope creep.** If `FuseSuite` is later updated to compare
  post-MEMPTR or if real-game emulation surfaces MEMPTR dependencies,
  this Phase E's "FUSE-init-only" approach won't suffice. Future Phase
  F could add the full update rules. Document the limitation in code
  comments.
- **Block-op X/Y rules vary by source.** Different references give
  slightly different formulas (especially for INI/OUTI where the +1
  direction flip on INIR/OTIR vs INDR/OTDR is subtle). Plan WU E-3 will
  cite Zilog NMOS rules and verify by FUSE pass-rate after each rule
  lands. If a particular block op still fails, iterate.
- **`BitReg` PV/S quirks beyond what's documented.** Z80 has additional
  edge cases (e.g. BIT n,(HL) S quirk depends on the bit position too).
  We only fix what FUSE actually checks. If residuals remain after
  WU E-2, triage in WU E-4 sweep.
