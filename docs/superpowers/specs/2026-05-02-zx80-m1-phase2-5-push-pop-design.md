# ZX Spectrum Emulator — Phase 2.5 Design (PUSH/POP rr)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Implement Z80 stack push and pop for register pairs: PUSH BC/DE/HL/AF and POP BC/DE/HL/AF. **8 opcodes across 2 Op classes.** Reuses the `Cpu.push` / `Cpu.pop` extension methods from Phase 2.4 (which were introduced specifically with this batch in mind).

The interesting wrinkle: PUSH/POP use a different register-pair encoding than LD rr,nn — `qq=11` means **AF**, not SP.

## Context

Phase 2.4 (planned, tag `m1-phase02-4`) added `Cpu.push(mem, value)` and `Cpu.pop(mem): Int` extension methods, used by CALL/RET/RST. Those helpers do exactly what PUSH/POP rr need. Phase 2.5 is therefore tiny — just two thin Op classes plus a small extension to the existing `RegPair` enum.

Reference top-level spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` (batch 2.5).

## Scope

### In scope

| Opcode | Mnemonic | T-states |
|--------|----------|----------|
| 0xC5 | PUSH BC | 11 |
| 0xD5 | PUSH DE | 11 |
| 0xE5 | PUSH HL | 11 |
| 0xF5 | PUSH AF | 11 |
| 0xC1 | POP BC | 10 |
| 0xD1 | POP DE | 10 |
| 0xE1 | POP HL | 10 |
| 0xF1 | POP AF | 10 |

Total: **8 opcode positions across 2 Op classes** (`PushPair(src)` and `PopPair(dst)`, both parameterized over `RegPair`).

Plus foundation: extend `RegPair` enum with a 5th value `AF`, plus a new `fromPushPopBits(bits)` companion helper. New `StackOps.installInto(decoder)` fragment.

### Out of scope (deferred)

- IX/IY-prefixed PUSH/POP (`PUSH IX`, `POP IY`) — DD/FD-prefixed; spec batch 2.8.
- `EX (SP), HL` — already implemented in Phase 2.1a (EX family).

## Architecture

### `RegPair` extension

The existing `RegPair` enum (BC/DE/HL/SP) gains a 5th value `AF`. Both `read` and `write` extend to handle AF via the existing `cpu.af` property accessor. A new `fromPushPopBits(bits)` companion helper maps the PUSH/POP-specific bit pattern.

```kotlin
enum class RegPair(val mnemonic: String) {
    BC("BC"),
    DE("DE"),
    HL("HL"),
    SP("SP"),
    AF("AF");   // new — only valid for PUSH/POP

    fun read(cpu: Cpu): Int = when (this) {
        BC -> cpu.bc
        DE -> cpu.de
        HL -> cpu.hl
        SP -> cpu.sp
        AF -> cpu.af
    }

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFFFF
        when (this) {
            BC -> cpu.bc = v
            DE -> cpu.de = v
            HL -> cpu.hl = v
            SP -> cpu.sp = v
            AF -> cpu.af = v
        }
    }

    companion object {
        /** Standard rr encoding: 00=BC, 01=DE, 10=HL, 11=SP. Used by LD rr,nn etc. */
        fun fromBits(bits: Int): RegPair = when (bits and 0x03) {
            0 -> BC; 1 -> DE; 2 -> HL; 3 -> SP
            else -> error("unreachable")
        }

        /** PUSH/POP encoding: 00=BC, 01=DE, 10=HL, 11=AF. */
        fun fromPushPopBits(bits: Int): RegPair = when (bits and 0x03) {
            0 -> BC; 1 -> DE; 2 -> HL; 3 -> AF
            else -> error("unreachable")
        }
    }
}
```

`fromBits` is unchanged — LD rr,nn callers still get SP at bits=3. Only PUSH/POP use `fromPushPopBits`.

### Op classes

Both classes guard against being instantiated with `RegPair.SP` (which would be a programming error, since neither PUSH nor POP supports SP).

| Class | Opcodes | T-states | operandLength | Behavior |
|-------|---------|----------|---------------|----------|
| `PushPair(src)` | `11 qq 0101` (4) | 11 | 0 | `cpu.push(mem, src.read(cpu))` |
| `PopPair(dst)` | `11 qq 0001` (4) | 10 | 0 | `dst.write(cpu, cpu.pop(mem))` |

PC advance: `(cpu.pc + 1) and 0xFFFF`. R increment: `cpu.bumpR()` (default by=1).

**No flags computed.** PUSH never touches F. POP AF *does* change F, but only because it writes the entire AF pair from the popped value — the Op class itself doesn't compute new flags.

Mnemonics: `"PUSH BC"`, `"POP AF"`.

### `StackOps.installInto(decoder)` fragment

`src/main/kotlin/ru/alepar/zx80/op/stack/StackOps.kt`. Loop-based:

```kotlin
object StackOps {
    fun installInto(d: Decoder) {
        // PUSH rr — 11 qq 0101 → C5, D5, E5, F5
        // POP rr  — 11 qq 0001 → C1, D1, E1, F1
        for (qqBits in 0..3) {
            val pair = RegPair.fromPushPopBits(qqBits)
            d.main[0xC5 or (qqBits shl 4)] = PushPair(src = pair)
            d.main[0xC1 or (qqBits shl 4)] = PopPair(dst = pair)
        }
    }
}
```

Wired into `OpTableBuilder.build()` with one `StackOps.installInto(d)` line.

### Test strategy

1. **Per-Op tests** asserting:
   - State of memory after PUSH (correct byte order: high byte at SP-1, low byte at SP-2).
   - State of register pair after POP.
   - SP after the op (decremented by 2 for PUSH, incremented by 2 for POP).
   - PC and R increments + T-state cost.
   - For PUSH: f preserved (not touched).
   - For POP AF specifically: f gets the low byte of the popped value.
   - Construction reject for RegPair.SP.

2. **`RegPair.fromPushPopBits` test** asserts the 4 mappings, distinct from `fromBits`.

3. **`RegPair.AF.read/write` tests** verify they read/write `cpu.af` correctly.

4. **`StackOps` fragment tests** assert all 8 opcode positions have the right Op via mnemonic match.

## Implementation Sequence

3 work units:

| WU | Subject | Approx scope |
|----|---------|--------------|
| **2.5-1** | RegPair extension (AF value + fromPushPopBits) + new StackOps skeleton + wire into OpTableBuilder | foundation; ~6 new tests |
| **2.5-2** | PushPair + PopPair + register all 8 opcodes via StackOps | 2 Op classes; ~16 tests |
| **2.5-3** | Sweep + tag `m1-phase02-5` | verification only |

## Risks

- **R1: PUSH/POP encoding has AF at qq=11, not SP.** Easy to use the wrong `fromXxxBits` helper. Mitigation: explicit `fromPushPopBits` test asserts the mapping; PUSH/POP install code uses `fromPushPopBits` not `fromBits`.
- **R2: POP AF writes F directly from popped byte.** Test sets the popped low byte to e.g. 0x55, asserts cpu.f becomes 0x55 after. Easy to forget that POP AF "modifies" flags.
- **R3: PUSH AF pushes A in high byte, F in low byte** — standard convention. Test verifies both bytes (high byte at SP-1, low byte at SP-2 — same as `cpu.push`'s contract).
- **R4: PUSH and POP must reject `RegPair.SP`.** `init { require(src != RegPair.SP) }` as a guard. Test asserts construction throws.
- **R5: Adding AF to RegPair could affect existing `fromBits` callers** if they exhaustively assert `entries.size`. Mitigation: review existing RegPair tests for size-sensitive assertions and update if needed (likely none, since RegPairTest doesn't assert entries.size).

## Done Criteria

1. `./gradlew check` green.
2. opcodes count climbs by 8.
3. nop_loop still passes.
4. `RegPair.AF` reads/writes `cpu.af`.
5. `RegPair.fromPushPopBits` maps `00/01/10/11 → BC/DE/HL/AF`.
6. Tag `m1-phase02-5` placed.
7. Phase 2.6 (rotates/shifts on A + DAA/CPL/SCF/CCF) is structurally unblocked.

## Open Questions (deferred to implementation)

- None significant. The design is mechanical given Phase 2.4's foundation.
