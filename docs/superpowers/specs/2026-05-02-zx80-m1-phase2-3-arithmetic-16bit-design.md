# ZX Spectrum Emulator — Phase 2.3 Design (16-bit arithmetic)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Implement Z80 16-bit arithmetic on register pairs: `ADD HL,rr`, `INC rr`, `DEC rr`, `ADC HL,rr`, `SBC HL,rr`. 20 new opcode positions across 5 Op classes. After this plan: opcodes count climbs by 20; FUSE pass-rate ticks up; composite score moves slightly higher.

The interesting wrinkles are: `INC rr` and `DEC rr` **don't touch flags at all** (unlike their 8-bit counterparts); `ADD HL,rr` only touches H/N/C (preserves S/Z/P/V); `ADC HL,rr` and `SBC HL,rr` are ED-prefixed and update all flags using 16-bit boundaries (bit 11 for H, bit 15 for C/S/V).

## Context

Phase 2.2 (planned, tag `m1-phase02-2`) covers the 8-bit ALU + INC/DEC. The `Flags` helpers (`afterAdd`/`afterSub`/etc.) and the `AluResult` data class are in place. The `RegPair` enum (BC/DE/HL/SP) from Phase 2.1b is in place. ED-prefix dispatch was wired in Phase 2.1a.

Reference top-level spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` (batch 2.3).

## Scope

### In scope

| Group | Opcodes | T-states | Op class |
|-------|---------|----------|----------|
| `ADD HL,rr` | 0x09 (BC), 0x19 (DE), 0x29 (HL), 0x39 (SP) | 11 | `AddHlPair(src)` |
| `INC rr` | 0x03 (BC), 0x13 (DE), 0x23 (HL), 0x33 (SP) | 6 | `IncPair(dst)` |
| `DEC rr` | 0x0B (BC), 0x1B (DE), 0x2B (HL), 0x3B (SP) | 6 | `DecPair(dst)` |
| `ADC HL,rr` (ED-prefixed) | ED 0x4A (BC), ED 0x5A (DE), ED 0x6A (HL), ED 0x7A (SP) | 15 | `AdcHlPair(src)` |
| `SBC HL,rr` (ED-prefixed) | ED 0x42 (BC), ED 0x52 (DE), ED 0x62 (HL), ED 0x72 (SP) | 15 | `SbcHlPair(src)` |

Total: **20 opcode positions across 5 Op classes**.

Plus foundation: extend `Flags` object with `afterAddWord(a, b, oldF)`, `afterAdcWord(a, b, oldF)`, `afterSbcWord(a, b, oldF)` helpers.

### Out of scope (deferred)

- 16-bit `ADD IX,rr` / `ADD IY,rr` (DD/FD-prefixed) — spec batch 2.8.
- 16-bit `LD SP,HL`, `LD SP,IX`, `LD SP,IY` — spec batch 2.5 / 2.8.
- Block ops, rotates, compare, etc. — later batches.

## Architecture

### Flag computation — extend `Flags` with three new helpers

`AluResult` data class is reused (its `value` field already accommodates 16-bit values; an Int holds 16 bits without issue).

```kotlin
object Flags {
    // ... existing 8-bit helpers ...

    /**
     * 16-bit ADD: a + b. Only H, N, C are computed. S, Z, P/V are
     * PRESERVED from oldF (this is unique to ADD HL,rr; ADC/SBC do
     * compute all flags).
     *
     * Flag rules:
     * - H = carry from bit 11 to bit 12 (i.e. (a & 0x0FFF) + (b & 0x0FFF) > 0x0FFF).
     * - N = 0.
     * - C = sum > 0xFFFF (carry from bit 15).
     * - S, Z, P/V = preserved from oldF.
     * - X, Y (undocumented) — not modeled.
     */
    fun afterAddWord(a: Int, b: Int, oldF: Int): AluResult

    /**
     * 16-bit ADC: a + b + carry. ALL flags computed.
     *
     * Flag rules:
     * - S = bit 15 of result.
     * - Z = result == 0.
     * - H = carry from bit 11 (with carry-in folded).
     * - P/V = signed overflow (operands same sign, result different sign).
     * - N = 0.
     * - C = sum > 0xFFFF.
     */
    fun afterAdcWord(a: Int, b: Int, oldF: Int): AluResult

    /**
     * 16-bit SBC: a - b - borrow. ALL flags computed.
     *
     * Flag rules:
     * - S = bit 15 of result.
     * - Z = result == 0.
     * - H = borrow from bit 12 (with borrow-in folded).
     * - P/V = signed overflow (operands different signs, result has different sign from a).
     * - N = 1.
     * - C = a - b - borrow < 0.
     */
    fun afterSbcWord(a: Int, b: Int, oldF: Int): AluResult
}
```

Per-helper unit tests cover the 16-bit boundaries: half-carry at `0x0FFF + 0x0001 → 0x1000` (H set); overflow at `0x7FFF + 0x0001 → 0x8000` (V/S set); carry at `0xFFFF + 0x0001 → 0x0000` (Z/C set, H also set).

### Op classes

| Class | Opcodes | T-states | R increment | operandLength | Behavior |
|-------|---------|----------|-------------|---------------|----------|
| `AddHlPair(src)` | `00 ss 1001` (4) | 11 | +1 | 0 | `val r = Flags.afterAddWord(cpu.hl, src.read(cpu), cpu.f); cpu.hl = r.value; cpu.f = r.newF` |
| `IncPair(dst)` | `00 dd 0011` (4) | 6 | +1 | 0 | `dst.write(cpu, (dst.read(cpu) + 1) and 0xFFFF)` — **no flags** |
| `DecPair(dst)` | `00 dd 1011` (4) | 6 | +1 | 0 | `dst.write(cpu, (dst.read(cpu) - 1) and 0xFFFF)` — **no flags** |
| `AdcHlPair(src)` | `ED 01 ss 1010` (4, ED-prefixed) | 15 | +2 | 0 | `val r = Flags.afterAdcWord(cpu.hl, src.read(cpu), cpu.f); cpu.hl = r.value; cpu.f = r.newF` |
| `SbcHlPair(src)` | `ED 01 ss 0010` (4, ED-prefixed) | 15 | +2 | 0 | `val r = Flags.afterSbcWord(cpu.hl, src.read(cpu), cpu.f); cpu.hl = r.value; cpu.f = r.newF` |

PC advance: `(cpu.pc + 1) and 0xFFFF` for main-table (AddHl/Inc/Dec); `(cpu.pc + 2) and 0xFFFF` for ED-prefixed (AdcHl/SbcHl). R increment via `cpu.bumpR()` (default by=1) for main-table; `cpu.bumpR(2)` for ED-prefixed.

Mnemonics: `"ADD HL, BC"`, `"INC BC"`, `"DEC HL"`, `"ADC HL, DE"`, `"SBC HL, SP"`. Comma-then-space convention.

### `AluOps.installInto` extension

Add three new private installers to the existing `AluOps` fragment (created in Phase 2.2):

```kotlin
object AluOps {
    fun installInto(d: Decoder) {
        // ... existing 2.2 installers ...
        installAddHl(d)
        installIncDecPair(d)
        installAdcSbcHl(d)
    }

    private fun installAddHl(d: Decoder) {
        // ADD HL,rr — 00 ss 1001 → 0x09, 0x19, 0x29, 0x39
        for (ssBits in 0..3) {
            val opcode = 0x09 or (ssBits shl 4)
            d.main[opcode] = AddHlPair(src = RegPair.fromBits(ssBits))
        }
    }

    private fun installIncDecPair(d: Decoder) {
        // INC rr — 00 dd 0011 → 0x03, 0x13, 0x23, 0x33
        // DEC rr — 00 dd 1011 → 0x0B, 0x1B, 0x2B, 0x3B
        for (ddBits in 0..3) {
            d.main[0x03 or (ddBits shl 4)] = IncPair(dst = RegPair.fromBits(ddBits))
            d.main[0x0B or (ddBits shl 4)] = DecPair(dst = RegPair.fromBits(ddBits))
        }
    }

    private fun installAdcSbcHl(d: Decoder) {
        // ADC HL,rr — ED 01 ss 1010 → ED 0x4A, ED 0x5A, ED 0x6A, ED 0x7A
        // SBC HL,rr — ED 01 ss 0010 → ED 0x42, ED 0x52, ED 0x62, ED 0x72
        for (ssBits in 0..3) {
            d.ed[0x4A or (ssBits shl 4)] = AdcHlPair(src = RegPair.fromBits(ssBits))
            d.ed[0x42 or (ssBits shl 4)] = SbcHlPair(src = RegPair.fromBits(ssBits))
        }
    }
}
```

### Test strategy

Three layers (same as Phase 2.2):

1. **Per-`Flags`-helper unit tests** in `FlagsTest.kt` (extending the existing file):
   - `afterAddWord`: basic, half-carry boundary at bit 11, carry boundary at bit 15, S/Z/PV-preservation.
   - `afterAdcWord`: basic, carry-in folding, half-carry, overflow, sign change.
   - `afterSbcWord`: basic, borrow-in, half-borrow, overflow, N flag set.

2. **Per-Op behavioral tests**:
   - `AddHlPair`: state delta, S/Z/PV preserved, H/N/C computed.
   - `IncPair` / `DecPair`: pair updated, **all flags untouched** (verify by setting f=0xFF beforehand and asserting it's unchanged).
   - `AdcHlPair` / `SbcHlPair`: ED-prefix → PC+=2, R+=2, 15 T-states; full flag updates.

3. **`AluOpsTest` extensions** asserting each opcode position has the right Op (mnemonic-based).

## Implementation Sequence

5 work units:

| WU | Subject | Approx scope |
|----|---------|--------------|
| **2.3-1** | `Flags` 16-bit helpers (afterAddWord/afterAdcWord/afterSbcWord) | 0 Op classes; ~15 unit tests on flag math. |
| **2.3-2** | `AddHlPair(src)` + AluOps.installAddHl | 1 Op class; ~8 tests; 4 opcodes. |
| **2.3-3** | `IncPair(dst)` + `DecPair(dst)` + AluOps.installIncDecPair | 2 Op classes; ~12 tests (flag invariance is the key assertion); 8 opcodes. |
| **2.3-4** | `AdcHlPair(src)` + `SbcHlPair(src)` + AluOps.installAdcSbcHl | 2 Op classes; ED-prefixed; ~12 tests; 8 opcodes. |
| **2.3-5** | Sweep + tag `m1-phase02-3` | verification only. |

## Risks

- **R1: `ADD HL,rr` flag preservation.** Easy to clobber S/Z/P/V. Mitigation: explicit test sets oldF=0xFF and asserts those bits are unchanged.
- **R2: 16-bit half-carry boundary is bit 11**, not bit 3. Different from 8-bit. Mitigation: explicit test at `0x0FFF + 0x0001 → H set`.
- **R3: 16-bit overflow boundary is bit 15.** PV from `(a^b) and 0x8000 == 0 && (a^result) and 0x8000 != 0`. Mitigation: explicit test at `0x7FFF + 0x0001 → V/S set`.
- **R4: ED-prefixed ops need PC+=2 and R+=2** (per `Im` precedent from Phase 2.1a). Mitigation: per-Op test asserts both.
- **R5: `INC rr` and `DEC rr` do NOT affect flags.** Easy to accidentally call `Flags.afterInc/Dec` (the 8-bit helpers). Mitigation: explicit "f preserved" test (set f=0xFF before, assert f=0xFF after).
- **R6: ADC HL,HL doubles HL.** Edge case worth a test (e.g. `HL=0x4000 + carry=0 → 0x8000`).

## Done Criteria

1. `./gradlew check` green.
2. opcodes count climbs by 20.
3. nop_loop still passes.
4. `Flags.afterAddWord/afterAdcWord/afterSbcWord` have comprehensive unit-test coverage.
5. Tag `m1-phase02-3` placed.
6. Phase 2.4 (jumps & calls) is structurally unblocked.

## Open Questions (deferred to implementation)

- Whether to factor out a small "16-bit half-carry" helper (`fun halfCarry16(a, b, c) = ...`) shared by afterAddWord/afterAdcWord. Probably yes — three call sites; trivial.
- Whether `IncPair` and `DecPair` share a tiny base class (they differ by one line: `+1` vs `-1`). Likely no — matches Phase 2.2's separate `IncReg`/`DecReg` precedent.
