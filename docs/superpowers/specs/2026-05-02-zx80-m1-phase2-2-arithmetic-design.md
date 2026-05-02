# ZX Spectrum Emulator — Phase 2.2 Design (8-bit ALU + INC/DEC)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Implement the entire Z80 8-bit arithmetic-logic family on the accumulator A — `ADD/ADC/SUB/SBC/AND/OR/XOR/CP A,r`, `…A,n`, `…A,(HL)` — plus `INC`/`DEC` for 8-bit operands. ~88 new opcode positions across 7 Op classes. After this plan: opcodes count climbs from ~94 to ~182; FUSE pass-rate jumps significantly; composite score moves from ~0.4 to ~0.6+.

This is the **first batch where Op classes actually compute flags**. Every prior batch left flags untouched. Flag computation is the load-bearing design concern; centralized in `Flags` helpers + an `AluOp` enum.

## Context

Phase 2.1a (tag `m1-phase02-1a`) and Phase 2.1b (planned, tag `m1-phase02-1b`) cover the LD family + dispatch + EX/misc. Score is on track for ~0.4 once 2.1b lands. The pattern for adding a family (per-Op TDD class + family-fragment with `installInto(decoder)` + builder line) is well-established.

Reference top-level spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` (batch 2.2 in the spec's batch table).

## Scope

### In scope

| Group | Opcodes | Op classes |
|-------|---------|------------|
| `ALU A,r` (8 ALU ops × 7 reg sources, opcodes `10 ooo rrr`) | 56 | `AluAReg(op, src)` |
| `ALU A,(HL)` (one per ALU op, opcodes `10 ooo 110`) | 8 | `AluAFromHl(op)` |
| `ALU A,n` (immediate, opcodes `11 ooo 110`) | 8 | `AluAImm(op)` |
| `INC r` (7 register variants, opcodes `00 rrr 100` minus rrr=110) | 7 | `IncReg(dst)` |
| `INC (HL)` (`0x34`) | 1 | `IncHlMem` |
| `DEC r` (7 register variants, opcodes `00 rrr 101` minus rrr=110) | 7 | `DecReg(dst)` |
| `DEC (HL)` (`0x35`) | 1 | `DecHlMem` |

Total: **88 opcode positions across 7 Op classes**.

Plus foundation: extended `Flags` object with helper functions, new `AluResult` data class, new `AluOp` enum, new `AluOps.installInto(decoder)` fragment.

### Out of scope (deferred)

- 16-bit arith (`ADD HL,rr`, `INC rr`, `DEC rr`, `ADC HL,rr` / `SBC HL,rr`) — spec batch 2.3.
- IX/IY indexed arithmetic (`ADD A,(IX+d)`, `INC (IX+d)`, etc.) — DD/FD-prefixed; spec batch 2.8.
- Block ALU ops — none exist; this isn't a thing for ALU.
- Rotates/shifts (`RLCA/RRCA/RLA/RRA/RLC/RRC/RL/RR/SLA/SRA/SRL`) — spec batches 2.6, 2.7.
- DAA, CPL, SCF, CCF — spec batch 2.6.
- Undocumented YF/XF flag bits (per project's non-goals).

## Architecture

### Flag computation — centralized in `Flags` helpers

Extend the existing `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt` object with these functions, returning a small `AluResult` data class:

```kotlin
data class AluResult(val value: Int, val newF: Int)

object Flags {
    // existing C/N/PV/Y/H/X/Z/S bit constants stay

    /** ADD/ADC: a + b + carry. Returns (sum & 0xFF, F). carry is 0 or 1. */
    fun afterAdd(a: Int, b: Int, carry: Int): AluResult

    /** SUB/SBC/CP: a - b - carry. Returns (diff & 0xFF, F). carry is 0 or 1. */
    fun afterSub(a: Int, b: Int, carry: Int): AluResult

    /** AND a, b. C cleared, H set, N cleared, P=parity. */
    fun afterAnd(a: Int, b: Int): AluResult

    /** OR a, b. C cleared, H cleared, N cleared, P=parity. */
    fun afterOr(a: Int, b: Int): AluResult

    /** XOR a, b. C cleared, H cleared, N cleared, P=parity. */
    fun afterXor(a: Int, b: Int): AluResult

    /** INC value. C preserved from oldF; N cleared; H from low nibble overflow; P/V=overflow. */
    fun afterInc(value: Int, oldF: Int): AluResult

    /** DEC value. C preserved from oldF; N set; H from low nibble borrow; P/V=overflow. */
    fun afterDec(value: Int, oldF: Int): AluResult

    /** True iff `value` (low 8 bits) has even parity (even number of 1 bits). */
    fun parity(value: Int): Boolean
}
```

**Per-helper unit tests** cover FUSE-relevant edges (zero, sign flip, half-carry boundary at `0x0F → 0x10`, overflow boundary at `0x7F → 0x80` and `0x80 → 0x7F`, parity edges, INC/DEC C-preservation). This is dense — ~30-40 unit tests in `FlagsTest.kt`.

### `AluOp` enum

`src/main/kotlin/ru/alepar/zx80/op/alu/AluOp.kt`:

```kotlin
enum class AluOp(val mnemonic: String, val updatesA: Boolean) {
    ADD("ADD", true),
    ADC("ADC", true),
    SUB("SUB", true),
    SBC("SBC", true),
    AND("AND", true),
    OR("OR", true),
    XOR("XOR", true),
    CP("CP", false);    // computes flags like SUB, leaves A alone

    fun apply(a: Int, b: Int, oldF: Int): AluResult = when (this) {
        ADD -> Flags.afterAdd(a, b, 0)
        ADC -> Flags.afterAdd(a, b, if (oldF and Flags.C != 0) 1 else 0)
        SUB, CP -> Flags.afterSub(a, b, 0)
        SBC -> Flags.afterSub(a, b, if (oldF and Flags.C != 0) 1 else 0)
        AND -> Flags.afterAnd(a, b)
        OR -> Flags.afterOr(a, b)
        XOR -> Flags.afterXor(a, b)
    }

    companion object {
        /** Map opcode bits 5-3 (the 'ooo' field) to an AluOp. */
        fun fromBits(bits: Int): AluOp = when (bits and 0x07) {
            0 -> ADD; 1 -> ADC; 2 -> SUB; 3 -> SBC
            4 -> AND; 5 -> XOR; 6 -> OR;  7 -> CP
            else -> error("unreachable")
        }
    }
}
```

(Note: Z80 ALU ooo encoding is `000=ADD, 001=ADC, 010=SUB, 011=SBC, 100=AND, 101=XOR, 110=OR, 111=CP` — XOR and OR are at 5 and 6 respectively, not 6 and 5.)

### Op classes

| Class | Opcodes | T-states | operandLength | Behavior |
|-------|---------|----------|---------------|----------|
| `AluAReg(op, src)` | `10 ooo rrr` minus rrr=110 | 4 | 0 | `val r = op.apply(cpu.a, src.read(cpu), cpu.f); if (op.updatesA) cpu.a = r.value; cpu.f = r.newF` |
| `AluAFromHl(op)` | `10 ooo 110` | 7 | 0 | reads `mem.read(cpu.hl)` as b, otherwise same |
| `AluAImm(op)` | `11 ooo 110` | 7 | 1 | reads `mem.read(cpu.pc + 1)` as b, otherwise same |
| `IncReg(dst)` | `00 rrr 100` minus rrr=110 | 4 | 0 | `val r = Flags.afterInc(dst.read(cpu), cpu.f); dst.write(cpu, r.value); cpu.f = r.newF` |
| `IncHlMem` | `0x34` | 11 | 0 | `val r = Flags.afterInc(mem.read(cpu.hl), cpu.f); mem.write(cpu.hl, r.value); cpu.f = r.newF` |
| `DecReg(dst)` | `00 rrr 101` minus rrr=110 | 4 | 0 | same shape with `afterDec` |
| `DecHlMem` | `0x35` | 11 | 0 | same shape with `afterDec` |

All ops follow Phase 2.1a/2.1b conventions: PC advance via `(pc + 1 + operandLength) and 0xFFFF`, `cpu.bumpR()`, `cpu.tStates += baseCycles`. The new wrinkle is they DO write `cpu.f` (every prior batch left it alone).

Mnemonics: `"ADD A, B"`, `"ADC A, (HL)"`, `"ADD A, n"`, `"INC B"`, `"INC (HL)"`, etc. (uppercase ALU mnemonic + comma-then-space + dst/src; same convention as LD).

### `AluOps.installInto(decoder)` fragment

`src/main/kotlin/ru/alepar/zx80/op/alu/AluOps.kt`. Loop-based registration:

```kotlin
object AluOps {
    fun installInto(d: Decoder) {
        installAluAReg(d)        // 56 opcodes in 0x80-0xBF (minus rrr=110 entries)
        installAluAFromHl(d)     // 8 opcodes (0x86, 0x8E, 0x96, 0x9E, 0xA6, 0xAE, 0xB6, 0xBE)
        installAluAImm(d)        // 8 opcodes (0xC6, 0xCE, 0xD6, 0xDE, 0xE6, 0xEE, 0xF6, 0xFE)
        installInc(d)            // 7 IncReg + 1 IncHlMem (0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C)
        installDec(d)            // 7 DecReg + 1 DecHlMem (0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x35, 0x3D)
    }
    // ... per-installer methods follow pattern of LdOps ...
}
```

### Test strategy

Three layers of testing:

1. **Per-`Flags`-helper unit tests** (~30-40 tests in `FlagsTest.kt`). Cover:
   - Each helper's basic functional case.
   - Half-carry boundary (`0x0F + 0x01 → 0x10` sets H).
   - Sign change (`0x7F + 0x01 → 0x80` sets S).
   - Overflow (`0x7F + 0x01 → V set`; `0x80 - 0x01 → V set`).
   - Zero detection (`0x00 + 0x00 → Z set`).
   - Parity for AND/OR/XOR (parity of `0x00`, `0x01`, `0xFF`).
   - INC/DEC: C bit preserved from oldF.

2. **Per-Op behavioral tests** for each of the 7 Op classes. Each asserts:
   - PC/R/T-state delta.
   - Result in correct register or memory cell.
   - `cpu.f` updated correctly (spot-check; full coverage is in Flags tests).
   - For CP: A unchanged.
   - For INC/DEC: C flag NOT touched.

3. **`AluOpsTest`** asserts each opcode position has the right `Op` instance via mnemonic matching.

## Implementation Sequence

6 work units:

| WU | Subject | Approx scope |
|----|---------|--------------|
| **2.2-1** | `Flags` helpers + `AluResult` data class + `AluOp` enum | ~40 unit tests on flag math; foundational. |
| **2.2-2** | `AluAReg` (the big 56-opcode block) + `AluOps` skeleton + wire into builder | 1 Op class + fragment + ~15 tests |
| **2.2-3** | `AluAFromHl` + register 8 opcodes | 1 Op class + ~6 tests |
| **2.2-4** | `AluAImm` + register 8 opcodes | 1 Op class + ~6 tests |
| **2.2-5** | `IncReg` + `IncHlMem` + `DecReg` + `DecHlMem` + register 16 opcodes | 4 Op classes + ~16 tests |
| **2.2-6** | Sweep + tag `m1-phase02-2` | verification only |

## Risks

- **R1: Flag computation has many edge cases.** Half-carry, overflow, parity. Mitigation: dense per-helper tests covering boundaries; FUSE catches anything missed.
- **R2: ADC/SBC carry-in.** Easy to forget folding incoming C bit. Mitigation: explicit tests with C=1.
- **R3: INC/DEC do NOT touch C flag** — unique among the family. Easy to accidentally clear it. Mitigation: explicit per-Op test asserts C is preserved.
- **R4: CP doesn't update A.** Tests verify both that flags are computed AND that A is unchanged.
- **R5: Z80 ALU `ooo` encoding has XOR at 5 and OR at 6** — easy to swap. Mitigation: `AluOp.fromBits` test verifies the exact mapping; FUSE catches mistakes.
- **R6: Trap B (FUSE tStatesToRun) still deferred** — all ops in this batch are single-step.

## Done Criteria

1. `./gradlew check` green.
2. opcodes count climbs by 88 (from prior + 88; expected ~182 if 2.1b lands first).
3. FUSE pass-rate climbs significantly (target: most ALU + INC/DEC opcode FUSE tests pass).
4. nop_loop still passes.
5. `Flags` helpers have comprehensive per-edge unit-test coverage.
6. `AluOp.fromBits` test verifies the Z80 encoding exactly.
7. Tag `m1-phase02-2` placed.
8. Phase 2.3 (16-bit arith) is structurally unblocked.

## Open Questions (deferred to implementation)

- Whether to factor `AluOp.apply`'s ADC/SBC carry-extract into a one-liner helper. Likely yes; trivial.
- Whether `IncReg`/`DecReg` share a private helper for the "read-modify-write a register slot" pattern. They differ only in `Flags.afterInc` vs `afterDec`; a single parameterized class with a direction enum could collapse them. Decide in WU 2.2-5; per the LdRegReg/LdRegImm precedent, separate classes per ALU operation is the established idiom.
- `IncReg(Reg.A)` vs `IncReg(Reg.B)` etc. differ only in which reg they target. Should `IncReg` be parameterized over `Reg`? Yes — matches the parameterization precedent.
