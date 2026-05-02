# ZX Spectrum Emulator — Phase 2.1a Design (Foundation: Dispatcher, OpTableBuilder, Misc + EX ops)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

First batch of Z80 instruction implementation. Establishes the dispatcher / OpTableBuilder pattern that all subsequent opcode batches will follow, and ships the simplest non-LD instruction families (misc + EX) so the harness produces its first non-zero score. Also fixes "Trap A" from the prior plan's final review (FuseSuite/ProgramsSuite must follow prefix bytes through to sub-tables, not look only at `decoder.main`).

## Context

The harness scaffolding (M1 Phase 0+1, tag `m1-phase01-harness-baseline`) is complete:

- 53 tests pass; `./gradlew check` is green.
- `./build/install/zx80/bin/zx80 score` reports `SCORE: 0.000  (opcodes 0/1792, fuse 0/1356, programs 0/1)`.
- `Cpu`, `Memory`, `Reg`, `Flags`, `Op`, `OperandFetcher`, `Decoder` types exist; `Decoder` holds seven all-null dispatch tables.
- Three suites (`OpcodeCoverage`, `FuseSuite`, `ProgramsSuite`) exist but currently report all failures.
- `FuseSuite.runOne` and `ProgramsSuite.runOne` do `decoder.main[opcodeByte]` only — they do **not** follow CB/ED/DD/FD prefix bytes. This is **Trap A**, fixed in this plan.

Reference spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md`. The full Z80 implementation is a multi-batch effort (the spec's batch table 2.1–2.11). This Phase 2.1a covers a strict subset of batch 2.1's spec scope.

## Scope

### In scope

- **Trap A fix:** new `Dispatcher` type that translates `(cpu, mem)` → `Op?` by reading the byte at PC, following any prefix into the right sub-table.
- **OpTableBuilder pattern:** top-level `OpTableBuilder.build(): Decoder` that calls per-family `installInto(decoder)` fragments. CLI swaps `Decoder()` for `OpTableBuilder.build()`.
- **Misc family** (5 distinct Op classes): `NOP`, `HALT`, `DI`, `EI`, `IM 0/1/2`.
- **EX family** (4 distinct Op classes): `EX AF,AF'`, `EXX`, `EX DE,HL`, `EX (SP),HL`.
- All registered via family fragments (`MiscOps`, `ExOps`).

### Deferred (explicit non-goals)

- **All LD ops** — that's Plan 2.1b (separate brainstorm + plan + execution).
- **Trap B fix** (FUSE `tStatesToRun` ignored, single-step only). No block ops in 2.1a; deferred to whichever batch introduces multi-iteration ops (LDIR in 2.10).
- **OpcodeCoverage denominator refinement** (the `1792` overcount). Deferred until enough opcodes exist to enumerate the documented opcode set meaningfully.
- **Real git info in `score.json`** (placeholder `?` strings). Cosmetic; not gating.
- **Other Z80 families** (arith, logic, rot, bit, branch, stack, io, block) — future plans 2.2–2.10.
- **Undocumented opcodes / flags / MEMPTR.**
- **ROM, ULA, keyboard, interrupts** beyond IFF/IM bookkeeping (M2 territory).

## Architecture

### Dispatcher — the Trap A fix

New file `src/main/kotlin/ru/alepar/zx80/cpu/Dispatcher.kt`:

```kotlin
class Dispatcher(private val decoder: Decoder) {
    /**
     * Read bytes from `mem` starting at `cpu.pc`, follow any prefix bytes
     * into the right sub-table, return the looked-up Op (or null if not
     * implemented). Does NOT advance PC or touch tStates — that's Op.execute's job.
     */
    fun decodeAt(cpu: Cpu, mem: Memory): Op? {
        val b0 = mem.read(cpu.pc)
        return when (b0) {
            0xCB -> decoder.cb[mem.read(cpu.pc + 1)]
            0xED -> decoder.ed[mem.read(cpu.pc + 1)]
            0xDD -> decodeIndexed(decoder.dd, decoder.ddcb, cpu, mem)
            0xFD -> decodeIndexed(decoder.fd, decoder.fdcb, cpu, mem)
            else -> decoder.main[b0]
        }
    }

    private fun decodeIndexed(
        prefixTable: Array<Op?>,
        cbTable: Array<Op?>,
        cpu: Cpu,
        mem: Memory,
    ): Op? {
        val b1 = mem.read(cpu.pc + 1)
        return if (b1 == 0xCB) {
            // DD CB d xx — opcode is byte 3 (after the d displacement)
            cbTable[mem.read(cpu.pc + 3)]
        } else {
            prefixTable[b1]
        }
    }
}
```

`mem.read` already wraps mod 64K, so `cpu.pc + 1`, `+ 3` are safe at any PC.

`FuseSuite.runOne` and `ProgramsSuite.runOne` change from looking up `decoder.main[opcodeByte]` to calling `dispatcher.decodeAt(cpu, mem)`. The Op itself still owns advancing PC past the entire instruction (prefix + opcode + operands).

### OpTableBuilder — per-family fragments

New file `src/main/kotlin/ru/alepar/zx80/op/OpTableBuilder.kt`:

```kotlin
object OpTableBuilder {
    fun build(): Decoder {
        val d = Decoder()
        MiscOps.installInto(d)
        ExOps.installInto(d)
        // Future: LdOps.installInto(d), ArithOps.installInto(d), ...
        return d
    }
}
```

Each family fragment lives in its own subdirectory next to its Op classes:

```
src/main/kotlin/ru/alepar/zx80/op/
  OpTableBuilder.kt
  misc/
    Nop.kt
    Halt.kt
    Di.kt
    Ei.kt
    Im.kt
    MiscOps.kt        // installInto(d): registers all of the above
  ex/
    ExAfAfAlt.kt
    Exx.kt
    ExDeHl.kt
    ExSpHl.kt
    ExOps.kt          // installInto(d): registers all of the above
```

Pattern for a family fragment:

```kotlin
object MiscOps {
    fun installInto(d: Decoder) {
        d.main[0x00] = Nop
        d.main[0x76] = Halt
        d.main[0xF3] = Di
        d.main[0xFB] = Ei
        d.ed[0x46] = Im(0)
        d.ed[0x56] = Im(1)
        d.ed[0x5E] = Im(2)
    }
}
```

### CLI integration

`ScoreCommand.kt` line 29-30 changes from:

```kotlin
val decoder = Decoder()
```

to:

```kotlin
val decoder = OpTableBuilder.build()
```

`FuseSuite` and `ProgramsSuite` constructors gain a `Dispatcher` parameter (or construct one internally from the decoder); their `runOne` methods replace `decoder.main[opcodeByte]` with `dispatcher.decodeAt(cpu, mem)`.

### Op classes

Each Op is a Kotlin `object` (singleton) when stateless, or a regular `class` when parameterized (only `Im(mode)` in this batch). Each must:

- Set `val operandLength: Int` (bytes consumed after opcode/prefix).
- Set `val baseCycles: Int` (T-states; spec for each below).
- In `execute(cpu, mem)`: advance `cpu.pc`, increment `cpu.r` (1 for unprefixed, 2 for prefixed), add `baseCycles` to `cpu.tStates`, do its work.
- Provide `mnemonic(operands): String`.

Per-op spec:

| Op | Opcode | Cycles | r incr | Notes |
|----|--------|--------|--------|-------|
| `Nop` | `0x00` | 4 | +1 | does nothing |
| `Halt` | `0x76` | 4 | +1 | sets `cpu.halted = true` |
| `Di` | `0xF3` | 4 | +1 | clears `iff1` and `iff2` |
| `Ei` | `0xFB` | 4 | +1 | sets `iff1` and `iff2` |
| `Im(0)` | `0xED 0x46` | 8 | +2 | sets `cpu.im = 0` |
| `Im(1)` | `0xED 0x56` | 8 | +2 | sets `cpu.im = 1` |
| `Im(2)` | `0xED 0x5E` | 8 | +2 | sets `cpu.im = 2` |
| `ExAfAfAlt` | `0x08` | 4 | +1 | swaps `(a,f)` with `(aAlt,fAlt)` |
| `Exx` | `0xD9` | 4 | +1 | swaps BC↔BC', DE↔DE', HL↔HL' (six byte fields) |
| `ExDeHl` | `0xEB` | 4 | +1 | swaps DE with HL (four byte fields) |
| `ExSpHl` | `0xE3` | 19 | +1 | exchanges `(SP)`↔L and `(SP+1)`↔H |

None of the above writes flag bits computed from a result. `EX AF,AF'` is the only one that touches F at all (by swapping it). All others leave F untouched.

### Test strategy

- **Per Op:** unit test asserting post-execute state delta — PC advance, r increment, tStates increment, target field/memory contents, flag invariance (or swap, for `EX AF,AF'`).
- **Dispatcher:** stub-Op tests that install a stub at each table position and verify routing through every prefix path (main, cb, ed, dd, fd, ddcb, fdcb). Including a test that places an opcode near `0xFFFD` to exercise wrap on `pc + 3` for DDCB.
- **OpTableBuilder:** test that `build()` produces a Decoder with the expected non-null entries (count check).
- **Integration smoke:** existing CLI test continues to pass with non-zero score; one new test that verifies the headline number is in the expected range (`> 0`).

## Implementation Sequence

5 work units, each: implementer dispatch → spec compliance review → code quality review → fix cycle if needed → mark complete.

| WU | Subject | Approximate scope |
|----|---------|-------------------|
| **2.1a-1** | `Dispatcher` (Trap A fix) + tests | One new production file, ~7 unit tests covering each prefix path including the wrap edge case. Decoder remains empty; FuseSuite/ProgramsSuite not yet rewired. Score still 0. |
| **2.1a-2** | `OpTableBuilder` skeleton + wire CLI/suites to `Dispatcher` | New `OpTableBuilder.kt` (empty `build()`); modify `ScoreCommand`, `FuseSuite`, `ProgramsSuite` to use it; update tests. Score still 0 because builder is empty. |
| **2.1a-3** | Misc family — `Nop`, `Halt`, `Di`, `Ei`, `Im` | 5 new Op classes + tests; `MiscOps.installInto`; wire into builder. `nop_loop` program now passes (programs 1/1). Some FUSE main-table cases pass. |
| **2.1a-4** | EX family — `ExAfAfAlt`, `Exx`, `ExDeHl`, `ExSpHl` | 4 new Op classes + tests; `ExOps.installInto`. `ExSpHl` is the trickiest (memory accesses, byte-order discipline). More FUSE cases pass. |
| **2.1a-5** | Sweep + tag | Run `./gradlew check`, verify score climbs end-to-end, run `./gradlew installDist && zx80 score`, capture the headline string. Tag commit `m1-phase02-1a`. |

Each WU pushes one or more focused commits. Final tag closes out the plan.

## Risks

- **R1: Dispatcher edge cases not covered by stub tests.** Real prefix Ops don't exist until 2.7+; the dispatcher tests only verify routing, not Op behavior under prefix dispatch. The first real CB Op may surface a routing bug. Mitigation: careful test coverage now (wrap, all prefix paths, DDCB displacement-then-opcode order).
- **R2: `EX (SP),HL` byte order.** Z80 is little-endian: `(SP)` ↔ `L`, `(SP+1)` ↔ `H`. Easy to reverse. FUSE catches it; write the test carefully against both pre- and post-state.
- **R3: First non-zero score may surprise the autonomous-loop diff.** `score.prev.json` rotation must work cleanly when score transitions from 0 to non-zero. Verify by running `score` twice consecutively.
- **R4: Implementer may try to "fix" Trap B opportunistically** in 2.1a even though the plan defers it. Trap B fix has design questions (how does the dispatcher decide a single LDIR iteration is "done enough"?) that aren't worth answering here. If the implementer asks, answer "deferred to 2.10."

## Done Criteria

1. `./gradlew check` green; all 53 prior tests + ~30+ new tests pass.
2. `./build/install/zx80/bin/zx80 score` reports a non-zero composite score.
3. `programs 1/1` (nop_loop passes).
4. `opcodes` count is exactly 11 (4 misc main: NOP/HALT/DI/EI + 3 misc ed: IM 0/1/2 + 4 ex main: EX AF/EXX/EX DE,HL/EX (SP),HL).
5. `fuse` count is non-zero (exact number depends on which FUSE cases are covered; the 9 Op classes here cover roughly 11 unique opcodes).
6. `Dispatcher` exercised for all 7 dispatch paths in unit tests.
7. `OpTableBuilder` is the only construction site for `Decoder` in production code (CLI doesn't construct `Decoder()` directly anywhere).
8. Tag `m1-phase02-1a` placed on the final commit.
9. Plan 2.1b (LD family) is structurally unblocked — adding a new family is a new fragment file + new Op classes + one line in `OpTableBuilder.build()`.

## Open Questions (deferred to implementation)

- Whether `Dispatcher` should be a constructor parameter to `FuseSuite`/`ProgramsSuite` or constructed internally from the decoder. Tactical; decide during WU 2.1a-2.
- Where the integer-vs-byte boundary lives for prefix bytes (`0xDD` is `Int 221` in our convention; comparing against `0xDD` literal works because Kotlin promotes; just be consistent).
- Whether `Im(mode: Int)` validates `mode in 0..2`. Probably yes via `require`. Decide in WU 2.1a-3.
