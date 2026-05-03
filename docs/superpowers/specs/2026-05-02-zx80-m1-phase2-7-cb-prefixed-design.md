# ZX Spectrum Emulator — Phase 2.7 Design (CB-prefixed: rotates, shifts, BIT/SET/RES)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Implement the entire CB-prefixed opcode table: 7 documented rotate/shift ops × 8 destinations = 56 opcodes, plus BIT/RES/SET × 8 bit positions × 8 destinations = 192 opcodes. **248 documented opcodes across 8 Op classes** (the 8 SLL slots at CB 0x30-0x37 stay null per the project's "documented Z80 only" non-goal). Biggest single batch in M1.

After this plan: opcode count climbs by 248. Composite score jumps significantly — the CB family is a large chunk of FUSE.

## Context

Phases 2.1a-2.6 (planned) cover the entire main-table 0x00-0xFF (modulo a handful of stragglers in 2.10), 16-bit arithmetic, control flow, stack, and the 4 rotate-A variants. The Dispatcher (Phase 2.1a-1) already routes the CB prefix into `decoder.cb`. Phase 2.7 fills that table.

Reference top-level spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` (batch 2.7).

## Scope

### In scope

| Block | Opcodes | What | Op class |
|-------|---------|------|----------|
| CB 0x00-0x07 | 7 documented (8 slots) | RLC r — bits 0-2 select dst | `RotShiftReg(op=RLC, src)` |
| CB 0x08-0x0F | 7 | RRC r | `RotShiftReg(op=RRC, src)` |
| CB 0x10-0x17 | 7 | RL r  | `RotShiftReg(op=RL, src)` |
| CB 0x18-0x1F | 7 | RR r  | `RotShiftReg(op=RR, src)` |
| CB 0x20-0x27 | 7 | SLA r | `RotShiftReg(op=SLA, src)` |
| CB 0x28-0x2F | 7 | SRA r | `RotShiftReg(op=SRA, src)` |
| CB 0x30-0x37 | 0 (8 slots, all undocumented SLL) | — | (left null) |
| CB 0x38-0x3F | 7 | SRL r | `RotShiftReg(op=SRL, src)` |
| (HL) variants | 7 | one per rotate/shift op at the rrr=110 slot of each row | `RotShiftHl(op)` |
| CB 0x40-0x7F | 64 | BIT n,r — bits 5-3 select n, bits 2-0 select dst | `BitReg(n, src)` + `BitHl(n)` for rrr=110 |
| CB 0x80-0xBF | 64 | RES n,r | `ResReg(n, dst)` + `ResHl(n)` for rrr=110 |
| CB 0xC0-0xFF | 64 | SET n,r | `SetReg(n, dst)` + `SetHl(n)` for rrr=110 |

Total: **248 opcode positions across 8 Op classes**:
- `RotShiftReg(op, src)` — 49 (7 ops × 7 register dests)
- `RotShiftHl(op)` — 7 (7 ops × (HL))
- `BitReg(n, src)` — 56
- `BitHl(n)` — 8
- `ResReg(n, dst)` — 56
- `ResHl(n)` — 8
- `SetReg(n, dst)` — 56
- `SetHl(n)` — 8

Plus foundation: extend `Flags` with 7 helpers (`afterRlc`, `afterRrc`, `afterRl`, `afterRr`, `afterSla`, `afterSra`, `afterSrl`), new `RotateOp` enum, new `CbOps.installInto(decoder)` fragment.

### Out of scope (deferred)

- DDCB/FDCB-prefixed indexed bit/rot ops (`RLC (IX+d)` etc.) — spec batch 2.9.
- SLL (undocumented) — non-goal.
- Undocumented BIT flag bits (S, P/V get specific values per FUSE; we leave them as 0). FUSE BIT cases will partially fail on these undocumented bit checks; that's accepted.

## Architecture

### `RotateOp` enum + 7 Flags helpers

`src/main/kotlin/ru/alepar/zx80/op/rot/RotateOp.kt` (extends the existing `op/rot/` package from Phase 2.6):

```kotlin
enum class RotateOp(val mnemonic: String) {
    RLC("RLC"), RRC("RRC"),
    RL("RL"), RR("RR"),
    SLA("SLA"), SRA("SRA"),
    SRL("SRL");

    fun apply(value: Int, oldF: Int): AluResult = when (this) {
        RLC -> Flags.afterRlc(value)
        RRC -> Flags.afterRrc(value)
        RL  -> Flags.afterRl(value, oldF)
        RR  -> Flags.afterRr(value, oldF)
        SLA -> Flags.afterSla(value)
        SRA -> Flags.afterSra(value)
        SRL -> Flags.afterSrl(value)
    }

    companion object {
        /** Map CB opcode bits 5-3 (the 'ooo' field) to a RotateOp.
         *  bits=6 means SLL (undocumented) — rejected. */
        fun fromBits(bits: Int): RotateOp = when (bits and 0x07) {
            0 -> RLC; 1 -> RRC
            2 -> RL;  3 -> RR
            4 -> SLA; 5 -> SRA
            6 -> error("bits=6 is SLL (undocumented); not modeled")
            7 -> SRL
            else -> error("unreachable")
        }
    }
}
```

The 7 `Flags.afterXxx` helpers all return `AluResult(value, newF)`:

| Helper | New value | C source | Other flags |
|--------|-----------|----------|-------------|
| `afterRlc(v)` | `((v shl 1) or (v shr 7)) and 0xFF` | bit 7 of v | S=bit7 of result, Z=result==0, H=0, P/V=parity, N=0 |
| `afterRrc(v)` | `((v shr 1) or ((v and 1) shl 7)) and 0xFF` | bit 0 of v | (same shape) |
| `afterRl(v, oldF)` | `((v shl 1) or oldC) and 0xFF` (oldC = 0 or 1) | bit 7 of v | (same shape) |
| `afterRr(v, oldF)` | `((v shr 1) or (oldC shl 7)) and 0xFF` | bit 0 of v | (same shape) |
| `afterSla(v)` | `(v shl 1) and 0xFF` | bit 7 of v | (same shape) |
| `afterSra(v)` | `((v shr 1) or (v and 0x80)) and 0xFF` (sign-extending) | bit 0 of v | (same shape) |
| `afterSrl(v)` | `(v shr 1) and 0xFF` (zero-fill) | bit 0 of v | (same shape) |

All compute S/Z/PV/H/N/C exactly as documented. **Crucially, these CB-prefixed rotates compute S/Z/PV from the result, unlike the rotate-A variants in Phase 2.6 which preserve them.**

### Op classes

| Class | Opcodes | T-states | R increment | Behavior |
|-------|---------|----------|-------------|----------|
| `RotShiftReg(op, src)` | 49 | 8 | +2 (CB-prefixed) | `val r = op.apply(src.read(cpu), cpu.f); src.write(cpu, r.value); cpu.f = r.newF` |
| `RotShiftHl(op)` | 7 | 15 | +2 | Read mem[HL], apply, write back, update F |
| `BitReg(n, src)` | 56 | 8 | +2 | `cpu.f = updated F (Z from bit test, H=1, N=0, C preserved)` — A unchanged. Validates `n in 0..7`. |
| `BitHl(n)` | 8 | 12 | +2 | Same logic but reads byte at mem[HL] |
| `ResReg(n, dst)` | 56 | 8 | +2 | `dst.write(cpu, dst.read(cpu) and (1 shl n).inv() and 0xFF)`. No flags. |
| `ResHl(n)` | 8 | 15 | +2 | Read mem[HL], clear bit n, write back. No flags. |
| `SetReg(n, dst)` | 56 | 8 | +2 | `dst.write(cpu, dst.read(cpu) or (1 shl n))`. No flags. |
| `SetHl(n)` | 8 | 15 | +2 | Read mem[HL], set bit n, write back. No flags. |

PC advance: `(cpu.pc + 2) and 0xFFFF` for all (CB prefix + opcode = 2 bytes). R increment via `cpu.bumpR(2)`.

Mnemonics: `"RLC B"`, `"RR (HL)"`, `"BIT 3, A"`, `"RES 7, (HL)"`, `"SET 0, C"`. The bit number rendered as a single digit; comma-then-space separators.

### `CbOps.installInto(decoder)` fragment

`src/main/kotlin/ru/alepar/zx80/op/cb/CbOps.kt`. Loop-based, uses `RotateOp.fromBits` (skips SLL) and `Reg.fromBits` / hard-coded for (HL):

```kotlin
object CbOps {
    fun installInto(d: Decoder) {
        installRotateShift(d)        // CB 0x00-0x3F (skipping 0x30-0x37 SLL)
        installBit(d)                // CB 0x40-0x7F
        installRes(d)                // CB 0x80-0xBF
        installSet(d)                // CB 0xC0-0xFF
    }

    private fun installRotateShift(d: Decoder) {
        for (oooBits in 0..7) {
            if (oooBits == 6) continue   // SLL — undocumented
            val op = RotateOp.fromBits(oooBits)
            for (rrrBits in 0..7) {
                val opcode = (oooBits shl 3) or rrrBits
                d.cb[opcode] = if (rrrBits == 6) RotShiftHl(op) else RotShiftReg(op, Reg.fromBits(rrrBits))
            }
        }
    }

    private fun installBit(d: Decoder) {
        for (n in 0..7) {
            for (rrrBits in 0..7) {
                val opcode = 0x40 or (n shl 3) or rrrBits
                d.cb[opcode] = if (rrrBits == 6) BitHl(n) else BitReg(n, Reg.fromBits(rrrBits))
            }
        }
    }

    private fun installRes(d: Decoder) {
        for (n in 0..7) {
            for (rrrBits in 0..7) {
                val opcode = 0x80 or (n shl 3) or rrrBits
                d.cb[opcode] = if (rrrBits == 6) ResHl(n) else ResReg(n, Reg.fromBits(rrrBits))
            }
        }
    }

    private fun installSet(d: Decoder) {
        for (n in 0..7) {
            for (rrrBits in 0..7) {
                val opcode = 0xC0 or (n shl 3) or rrrBits
                d.cb[opcode] = if (rrrBits == 6) SetHl(n) else SetReg(n, Reg.fromBits(rrrBits))
            }
        }
    }
}
```

Wired into `OpTableBuilder.build()` with one `CbOps.installInto(d)` line.

### Test strategy

1. **Per-Flags-helper tests** in `FlagsTest.kt` (extending the existing file): ~21 tests (3 per helper covering basic, edge cases like 0x00 → 0x00 with Z set, and the bit shifted into C).

2. **Per-Op behavioral tests** for each of the 8 Op classes: state delta + PC/R/T-state assertions; for BIT/SET/RES tests across multiple bit positions (n=0, n=3, n=7).

3. **`CbOpsTest`** asserts each opcode position has the right Op via mnemonic match. Also explicitly asserts the SLL slots (CB 0x30-0x37) remain null.

## Implementation Sequence

6 work units:

| WU | Subject | Op classes | Opcodes |
|----|---------|------------|---------|
| **2.7-1** | RotateOp enum + 7 Flags helpers + dense per-edge tests | 0 | 0 |
| **2.7-2** | RotShiftReg + RotShiftHl + new CbOps fragment + wire into builder + register 56 rotate/shift opcodes | 2 | 56 |
| **2.7-3** | BitReg + BitHl + register 64 BIT opcodes | 2 | 64 |
| **2.7-4** | ResReg + ResHl + register 64 RES opcodes | 2 | 64 |
| **2.7-5** | SetReg + SetHl + register 64 SET opcodes | 2 | 64 |
| **2.7-6** | Sweep + tag `m1-phase02-7` | 0 | 0 |

## Risks

- **R1: SLA's bit 0 is always 0** (logical left shift). Don't accidentally rotate.
- **R2: SRA preserves the sign bit** (arithmetic right shift). Distinct from SRL (zero-fill). Mitigation: explicit test with `0x80 → 0xC0` (sign preserved) for SRA and `0x80 → 0x40` (zero-fill) for SRL.
- **R3: RL/RR fold the OLD C bit IN** as the new bit 0/7. Test with C=1 explicitly.
- **R4: All CB rotate/shift ops compute S/Z/PV from result** (unlike Phase 2.6's rotate-A variants which preserve them). Mitigation: explicit "Z is set when result is 0" tests.
- **R5: CB ops are ED-style prefixed** — PC+=2, R+=2, but the prefix and opcode are at consecutive PC positions (no displacement byte like DDCB). Mitigation: Dispatcher already handles this; verify with the per-Op tests.
- **R6: SLL (CB 0x30-0x37) intentionally null.** FUSE will report 8 failing cases. Accept; these go in the undocumented bucket. Mitigation: explicit CbOpsTest assertion that those 8 slots are null.
- **R7: BIT n,(HL) is 12 T-states**, not 15 (since it only reads). Easy to type 15 by analogy with SET/RES (HL). Mitigation: explicit per-Op test on T-states.
- **R8: BIT preserves C and ignores S/P/V** (officially undocumented for those bits — we leave them at 0). Tests assert C is preserved.

## Done Criteria

1. `./gradlew check` green.
2. opcodes count climbs by 248.
3. nop_loop still passes.
4. The 8 SLL slots (CB 0x30-0x37) are null in `decoder.cb` (verified by test).
5. FUSE pass rate climbs significantly (CB is a large chunk of the corpus).
6. Tag `m1-phase02-7` placed.
7. Phase 2.8 (DD/FD-prefixed IX/IY) is structurally unblocked.

## Open Questions (deferred to implementation)

- Whether the 4 BIT/RES/SET fragment installers can share a tiny "iterate (n, rrr) and install (HL) variant at rrr=110" helper. Likely yes — saves ~12 lines of duplication; decide in WU 2.7-3.
- Whether `BitReg`/`SetReg`/`ResReg` should `require(n in 0..7)` in init. Yes — matches the `Im(mode)` and `Rst(target)` precedents.
