# ZX Spectrum Emulator — Phase 2.1b Design (LD family)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Implement the full Z80 LD (load) family: ~80 new opcode positions across ~12 Op classes. Builds on Phase 2.1a's foundation (Dispatcher, OpTableBuilder, per-family fragments, `Cpu.bumpR`, `Reg` enum). After this plan: opcodes count climbs from 11 to ~91; FUSE pass-rate climbs significantly; composite score moves from `0.106` to roughly `0.35–0.50`.

## Context

Phase 2.1a (tag `m1-phase02-1a`) shipped: dispatch infrastructure + 11 opcodes (NOP/HALT/DI/EI/IM 0/1/2 + EX family). Score is `SCORE: 0.106  (opcodes 11/1792, fuse 10/1356, programs 1/1)`. The pattern for adding a family is established and uniform. `Reg` enum (B/C/D/E/H/L/A) exists but isn't used yet — the LD family is its first consumer.

Reference top-level spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` (batch 2.1 in the spec's batch table).

## Scope

### In scope

LD opcodes broken down by group:

| Group | Opcodes | Op classes |
|-------|---------|------------|
| `LD r,r'` (the 0x40-0x7F block, minus `0x76=HALT`) | 49 reg-to-reg + 7 `LD r,(HL)` + 7 `LD (HL),r` = **63** | `LdRegReg(src, dst)`, `LdRegFromHl(dst)`, `LdHlFromReg(src)` |
| `LD r,n` (8-bit immediate) + `LD (HL),n` | 7 + 1 = **8** | `LdRegImm(dst)`, `LdHlMemImm` |
| `LD rr,nn` (16-bit immediate to pair) | **4** (BC/DE/HL/SP) | `LdPairImm(pair)` |
| LD between A and indirect register pair | **4** (LD A,(BC), LD A,(DE), LD (BC),A, LD (DE),A) | `LdAFromBcDe(pair)`, `LdBcDeFromA(pair)` |
| `LD A,(nn)` / `LD (nn),A` | **2** | `LdAFromAddr`, `LdAddrFromA` |
| `LD HL,(nn)` / `LD (nn),HL` | **2** | `LdHlFromAddr`, `LdAddrFromHl` |

Plus foundation: `Reg.fromBits(bits)` companion helper, `RegPair` enum, `LdOps.installInto(decoder)` fragment, KDoc clarification on `Op.operandLength`.

Total: ~83 opcode positions, ~12 Op classes.

### Out of scope (deferred)

- IX/IY indexed LD ops (`LD r,(IX+d)` etc.) — DD/FD-prefixed; spec batch 2.8.
- Block LD ops (`LDI`, `LDIR`, `LDD`, `LDDR`) — ED-prefixed; spec batch 2.10.
- `LD I,A` / `LD A,I` / `LD R,A` / `LD A,R` — ED-prefixed; spec batch 2.10.
- `LD SP,HL` and `LD SP,IX`/`LD SP,IY` (16-bit reg-to-reg LD) — spec batch 2.5 / 2.8.
- Disassembler that consumes `Op.operandLength`. Field is set but unread until then.
- `Reg.fromBits` is added; the analogous `RegPair.fromBits` for the PUSH/POP encoding (where bits=11 means AF instead of SP) is deferred to spec batch 2.5.

## Architecture

### `Reg.fromBits(bits: Int): Reg`

Companion helper added to `src/main/kotlin/ru/alepar/zx80/cpu/Reg.kt`. Maps Z80 r-field bit patterns to `Reg` enum values.

```kotlin
companion object {
    /**
     * Map a Z80 r-field bit pattern (0..7) to the corresponding [Reg].
     * Bits=6 means `(HL)` — a memory access, not a register — and is rejected.
     * Callers handling `(HL)`-bearing opcodes branch on bits before calling this.
     */
    fun fromBits(bits: Int): Reg {
        require(bits in 0..7) { "bits must be in 0..7; got $bits" }
        require(bits != 6) { "bits=6 is (HL), not a register" }
        return when (bits) {
            0 -> B; 1 -> C; 2 -> D; 3 -> E
            4 -> H; 5 -> L; 7 -> A
            else -> error("unreachable")
        }
    }
}
```

### `RegPair` enum (new file)

`src/main/kotlin/ru/alepar/zx80/cpu/RegPair.kt`. Models 16-bit register pairs that LD ops can name. Uses the BC/DE/HL/SP encoding (the PUSH/POP encoding with AF at bits=11 is a separate concern for later).

```kotlin
package ru.alepar.zx80.cpu

enum class RegPair(val mnemonic: String) {
    BC("BC"), DE("DE"), HL("HL"), SP("SP");

    fun read(cpu: Cpu): Int = when (this) {
        BC -> cpu.bc
        DE -> cpu.de
        HL -> cpu.hl
        SP -> cpu.sp
    }

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFFFF
        when (this) {
            BC -> cpu.bc = v
            DE -> cpu.de = v
            HL -> cpu.hl = v
            SP -> cpu.sp = v
        }
    }

    companion object {
        /** Map a Z80 register-pair bit pattern (bits 4-5 of opcode, 0..3) to [RegPair]. */
        fun fromBits(bits: Int): RegPair = when (bits and 0x03) {
            0 -> BC; 1 -> DE; 2 -> HL; 3 -> SP
            else -> error("unreachable")
        }
    }
}
```

Tests cover read/write round-trip per pair, masking on write, and `fromBits`.

### Op class shapes

All LD ops follow Phase 2.1a conventions: `operandLength` set truthfully, `baseCycles` per Z80 spec, `execute()` advances PC, calls `cpu.bumpR()`, adds T-states, does work. None touches flags.

Per-Op T-state and operand-length spec:

| Op class | Opcodes | T-states | operandLength | Behavior |
|---|---|---|---|---|
| `LdRegReg(src, dst)` | 0x40..0x7F minus HALT and (HL) variants | 4 | 0 | `dst.write(cpu, src.read(cpu))` |
| `LdRegFromHl(dst)` | 0x46, 0x4E, 0x56, 0x5E, 0x66, 0x6E, 0x7E | 7 | 0 | `dst.write(cpu, mem.read(cpu.hl))` |
| `LdHlFromReg(src)` | 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x77 | 7 | 0 | `mem.write(cpu.hl, src.read(cpu))` |
| `LdRegImm(dst)` | 0x06, 0x0E, 0x16, 0x1E, 0x26, 0x2E, 0x3E | 7 | 1 | `dst.write(cpu, mem.read(cpu.pc + 1))` |
| `LdHlMemImm` | 0x36 | 10 | 1 | `mem.write(cpu.hl, mem.read(cpu.pc + 1))` |
| `LdPairImm(pair)` | 0x01, 0x11, 0x21, 0x31 | 10 | 2 | reads little-endian word from `pc+1..pc+2`, writes to pair |
| `LdAFromBcDe(pair)` | 0x0A (BC), 0x1A (DE) | 7 | 0 | `cpu.a = mem.read(pair.read(cpu))` |
| `LdBcDeFromA(pair)` | 0x02 (BC), 0x12 (DE) | 7 | 0 | `mem.write(pair.read(cpu), cpu.a)` |
| `LdAFromAddr` | 0x3A | 13 | 2 | reads address word from `pc+1..pc+2`, then `cpu.a = mem.read(addr)` |
| `LdAddrFromA` | 0x32 | 13 | 2 | reads address, then `mem.write(addr, cpu.a)` |
| `LdHlFromAddr` | 0x2A | 16 | 2 | reads address, then `cpu.l = mem.read(addr); cpu.h = mem.read(addr + 1)` |
| `LdAddrFromHl` | 0x22 | 16 | 2 | reads address, then `mem.write(addr, cpu.l); mem.write(addr + 1, cpu.h)` |

PC advance per Op: `(cpu.pc + 1 + operandLength) and 0xFFFF`.

### `LdOps.installInto(decoder)` fragment

Lives at `src/main/kotlin/ru/alepar/zx80/op/ld/LdOps.kt`. Three patterns of registration:

1. **Loop-based** for `LD r,r'` (the 0x40-0x7F block minus HALT and (HL) variants): iterate dst bits 0..7, src bits 0..7; skip `dst==6 || src==6`; skip the HALT slot at 0x76; install `LdRegReg(src=Reg.fromBits(srcBits), dst=Reg.fromBits(dstBits))` at `0x40 | (dstBits shl 3) | srcBits`.
2. **Loop-based** for `LD r,(HL)` (dst row, src bits=110): install `LdRegFromHl(dst=Reg.fromBits(dstBits))` at `0x40 | (dstBits shl 3) | 6`. Same for `LD (HL),r`.
3. **Loop-based** for the rest (LD r,n, LD rr,nn, LD r,(BC)/(DE) variants — all have natural opcode patterns).
4. **Direct** for the singleton ops (LD A,(nn), LD (nn),A, LD HL,(nn), LD (nn),HL, LD (HL),n).

Wired into `OpTableBuilder.build()` with a single `LdOps.installInto(d)` line.

### `Op.operandLength` KDoc clarification

Update the existing `Op` interface KDoc to clarify the field's role:

```
/**
 * Bytes after the opcode byte (and any prefixes) that this instruction
 * consumes — the operand bytes (n, nn, d). Informational metadata; not
 * read by the runtime today, but reserved for future disassembler use.
 *
 * Each Op is responsible for advancing PC inside execute() by
 * `1 + prefixBytes + operandLength`.
 */
val operandLength: Int
```

### Test strategy

Each Op class gets a per-Op test asserting state delta + PC advance + R increment + T-state cost + flag invariance. Each parameterized class also gets a "round-trip across all variants" test (e.g. `LdRegReg` over all 49 (src, dst) combos). `LdOpsTest` asserts each opcode position has the right Op installed (using mnemonic-based assertions for parameterized cases, per the WU 2.1a-3 review's tightening pattern).

## Implementation Sequence

6 work units, each: implementer dispatch → spec compliance review → code quality review → fix cycle if needed → mark complete.

| WU | Subject | Approx scope |
|----|---------|--------------|
| **2.1b-1** | Foundation: `Reg.fromBits`, `RegPair` enum + tests, `Op.operandLength` doc | 0 new Op classes; ~6-10 new tests. |
| **2.1b-2** | `LD r,r'` family: `LdRegReg`, `LdRegFromHl`, `LdHlFromReg`. The big one. | 3 new Op classes; ~20 tests; 63 opcodes. |
| **2.1b-3** | `LD r,n` family: `LdRegImm`, `LdHlMemImm`. | 2 new Op classes; ~10 tests; 8 opcodes. |
| **2.1b-4** | `LD rr,nn` family: `LdPairImm`. | 1 new Op class; ~6 tests; 4 opcodes. |
| **2.1b-5** | LD with memory addresses: `LdAFromBcDe`, `LdBcDeFromA`, `LdAFromAddr`, `LdAddrFromA`, `LdHlFromAddr`, `LdAddrFromHl`. | 4-6 new Op classes; ~12 tests; 8 opcodes. |
| **2.1b-6** | Sweep + tag `m1-phase02-1b` | 0 new code; verification only. |

## Risks

- **R1: `LdOps.installInto` is the largest fragment yet** (3 nested loops + ~10 direct registrations). Easy to off-by-one a bit pattern. Mitigation: `LdOpsTest` verifies a representative sample of registrations by mnemonic; FUSE catches the rest.
- **R2: Z80 endianness for `LD rr,nn` and friends.** Operand bytes are little-endian: `mem[pc+1]` is low, `mem[pc+2]` is high. Easy to reverse. Mitigation: per-Op tests assert this concretely.
- **R3: `LdHlFromAddr` and `LdAddrFromHl` perform two memory operations.** `(addr)` ↔ `L`, `(addr+1)` ↔ `H` — same little-endian convention as `EX (SP),HL`. Read both, then write both, to avoid mid-op state leak.
- **R4: PC advance in ops with operandLength=2** must use `(pc + 3) and 0xFFFF`, not `+1`. The new KDoc on `operandLength` should make this clear; per-Op tests verify final PC.
- **R5: Loop in `LdOps.installInto` for the 0x40-0x7F block must skip three things**: `dst==6` (those are LD (HL),r → handled by separate loop), `src==6` (those are LD r,(HL) → handled by separate loop), and the HALT slot at 0x76 (which would otherwise collide). Mitigation: explicit conditionals; test asserts main[0x76] is still HALT after LdOps installs.

## Done Criteria

1. `./gradlew check` green; ~50-80 new tests pass (102 prior + ~70 = ~170 total).
2. `./build/install/zx80/bin/zx80 score` reports composite score significantly above 0.106 (target: 0.35–0.50).
3. opcodes count: ~91 (11 prior + 80).
4. `programs 1/1` unchanged.
5. fuse count climbs by hundreds (LD ops are a large chunk of FUSE corpus).
6. `LdOps.installInto` is the only registration site for LD ops.
7. `Reg.fromBits` and `RegPair` exist with full TDD coverage.
8. Tag `m1-phase02-1b` placed.
9. Phase 2.2 (arithmetic) is structurally unblocked.

## Open Questions (deferred to implementation)

- Whether `LdAFromBcDe` and `LdBcDeFromA` are one parameterized class each (over `RegPair.BC` / `RegPair.DE`) or four separate object singletons. Tactical; decide in WU 2.1b-5. Parameterized matches precedent.
- Whether to factor out a small "address fetcher" helper (`fun readAddr(mem, pc): Int = mem.read(pc + 1) or (mem.read(pc + 2) shl 8)`) shared by `LdPairImm`, `LdAFromAddr`, `LdAddrFromA`, `LdHlFromAddr`, `LdAddrFromHl`. Probably yes — 5 use sites is enough to justify a helper. Decide in WU 2.1b-4 when the second user lands.
