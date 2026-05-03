# ZX Spectrum Emulator — Phase 2.8 Design (DD/FD-prefixed IX/IY)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Implement the DD-prefixed (IX) and FD-prefixed (IY) Z80 opcode tables. ~79 documented opcode positions across ~18 Op classes. Most are mechanical adaptations of HL-based ops, with two new wrinkles: signed 8-bit displacement for `(IX+d)` / `(IY+d)` addressing, and consistent +4 T-state penalty for the prefix.

After this plan: opcode count climbs by ~79; FUSE pass-rate ticks up modestly (DD/FD subset of FUSE is smaller than the CB block, but still substantial).

## Context

Phases 2.1a–2.7 (planned) cover all main-table and CB-prefixed documented opcodes. The Dispatcher (Phase 2.1a) already routes DD/FD prefixes into `decoder.dd` / `decoder.fd`. Phase 2.8 fills those tables.

Reference top-level spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` (batch 2.8).

## Scope

### In scope

**Group A — pair-touching IX/IY (26 opcodes per family + 1 main-table straggler):**

| DD opcode | Mnemonic | T-states |
|-----------|----------|----------|
| DD 21 nn nn | LD IX, nn | 14 |
| DD 22 nn nn | LD (nn), IX | 20 |
| DD 23 | INC IX | 10 (no flags) |
| DD 29 | ADD IX, IX | 15 |
| DD 2A nn nn | LD IX, (nn) | 20 |
| DD 2B | DEC IX | 10 (no flags) |
| DD 09 | ADD IX, BC | 15 |
| DD 19 | ADD IX, DE | 15 |
| DD 39 | ADD IX, SP | 15 |
| DD E1 | POP IX | 14 |
| DD E3 | EX (SP), IX | 23 |
| DD E5 | PUSH IX | 15 |
| DD E9 | JP (IX) | 8 |
| DD F9 | LD SP, IX | 10 |

Plus FD-twin counterparts (same shape with IY).

**Straggler in main:** `LD SP, HL` (0xF9, 6 T-states). Wasn't covered in Phases 2.1b/2.5; included here alongside `LD SP,IX` and `LD SP,IY` for natural grouping.

**Group B — indexed memory `(IX+d)` / `(IY+d)` variants (50 opcodes):**

| DD opcode | Mnemonic | T-states | operandLength (after prefix) |
|-----------|----------|----------|------------------------------|
| DD 46 d (and 4E/56/5E/66/6E/7E) | LD r,(IX+d) — 7 dst | 19 | 2 (d + ?) — actually displacement only, opcode at +1 |
| DD 70 d (..76 skipped, 77) | LD (IX+d),r — 7 src | 19 | 2 |
| DD 36 d n | LD (IX+d), n | 19 | 3 |
| DD 86 d (..BE) | ALU A,(IX+d) — 8 ALU ops | 19 | 2 |
| DD 34 d | INC (IX+d) | 23 | 2 |
| DD 35 d | DEC (IX+d) | 23 | 2 |

Plus FD twins. Each computes the effective address as `(idx.read(cpu) + signedD) and 0xFFFF`.

**Group C — JP (IX) / JP (IY)** — already covered in Group A above (DD E9 / FD E9).

Total: **~79 opcode positions across ~18 Op classes** (most parameterized over `IndexReg`).

### Out of scope (deferred)

- DDCB/FDCB (indexed bit/rotate ops) — spec batch 2.9 (next).
- IXH/IXL/IYH/IYL register halves (undocumented per spec non-goals).
- ED-prefixed remainder (LDIR/etc., I/O, NEG, RETI/RETN, LD I,A) — spec batch 2.10.

## Architecture

### `IndexReg` enum

`src/main/kotlin/ru/alepar/zx80/cpu/IndexReg.kt`:

```kotlin
enum class IndexReg(val mnemonic: String) {
    IX("IX"),
    IY("IY");

    fun read(cpu: Cpu): Int = if (this == IX) cpu.ix else cpu.iy

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFFFF
        if (this == IX) cpu.ix = v else cpu.iy = v
    }
}
```

Used by all DD/FD-prefixed Op classes.

### Op class shapes

All DD/FD ops:
- `cpu.bumpR(2)` (prefix + opcode = 2 M1 cycles)
- PC advance per instruction length (varies)
- Cycle counts as documented per opcode

Pair-touching (no `(IX+d)` operand):

| Class | Opcode pattern | T | PC adv | operandLength | Behavior |
|-------|----------------|---|--------|---------------|----------|
| `LdIxImm(idx)` | DD/FD 21 nn nn | 14 | +4 | 2 | reads nn from pc+2..pc+3, writes to idx |
| `LdIxFromAddr(idx)` | DD/FD 2A nn nn | 20 | +4 | 2 | reads addr; idx = mem.readWord(addr) |
| `LdAddrFromIx(idx)` | DD/FD 22 nn nn | 20 | +4 | 2 | reads addr; mem.writeWord(addr, idx) |
| `IncIx(idx)` | DD/FD 23 | 10 | +2 | 0 | idx += 1 (mod 64K); no flags |
| `DecIx(idx)` | DD/FD 2B | 10 | +2 | 0 | idx -= 1 (mod 64K); no flags |
| `AddIxPair(idx, srcBits)` | DD/FD 09/19/29/39 | 15 | +2 | 0 | reads src by srcBits (BC/DE/Self/SP); idx += src; flags as `Flags.afterAddWord` |
| `PushIx(idx)` | DD/FD E5 | 15 | +2 | 0 | `cpu.push(mem, idx.read(cpu))` |
| `PopIx(idx)` | DD/FD E1 | 14 | +2 | 0 | `idx.write(cpu, cpu.pop(mem))` |
| `ExSpIx(idx)` | DD/FD E3 | 23 | +2 | 0 | swaps idx with word at SP, like `EX (SP),HL` |
| `LdSpFromIx(idx)` | DD/FD F9 | 10 | +2 | 0 | `cpu.sp = idx.read(cpu)` |
| `JpIx(idx)` | DD/FD E9 | 8 | (set, not advanced) | 0 | `cpu.pc = idx.read(cpu)` |

Indexed `(IX+d)`:

| Class | Opcode pattern | T | PC adv | operandLength | Behavior |
|-------|----------------|---|--------|---------------|----------|
| `LdRegFromIxd(idx, dst)` | DD/FD 46+8*r | 19 | +3 | 1 | dst = mem.read(idx + signedD) |
| `LdIxdFromReg(idx, src)` | DD/FD 70+r (skip rrr=110) | 19 | +3 | 1 | mem.write(idx + signedD, src) |
| `LdIxdImm(idx)` | DD/FD 36 d n | 19 | +4 | 2 | mem.write(idx + signedD, n) |
| `AluAFromIxd(idx, op)` | DD/FD 86+8*ooo | 19 | +3 | 1 | applies AluOp.apply(cpu.a, mem.read(idx + signedD), cpu.f) |
| `IncIxd(idx)` | DD/FD 34 d | 23 | +3 | 1 | mem[idx+d] = afterInc(mem[idx+d], oldF) |
| `DecIxd(idx)` | DD/FD 35 d | 23 | +3 | 1 | mem[idx+d] = afterDec(mem[idx+d], oldF) |

Plus the straggler:
- `LdSpHl` (singleton, opcode 0xF9 main) — 6 T-states; no flags. `cpu.sp = cpu.hl`.

### Effective address computation

For all `(IX+d)` / `(IY+d)` ops:

```kotlin
val displacement = mem.read(cpu.pc + 2).toByte().toInt()   // signed -128..+127
val addr = (idx.read(cpu) + displacement) and 0xFFFF
```

For `LdIxdImm`, the displacement is at pc+2 and the immediate value at pc+3.

### `IxOps.installInto(decoder)` fragment

`src/main/kotlin/ru/alepar/zx80/op/ix/IxOps.kt`. Loops over both DD and FD tables for each Op family:

```kotlin
object IxOps {
    fun installInto(d: Decoder) {
        installPairTouching(d)
        installIndexedLd(d)
        installIndexedLdImm(d)
        installIndexedAlu(d)
        installIndexedIncDec(d)
        installJpIx(d)
        installLdSpHlStraggler(d)
    }

    private fun installPairTouching(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            table[0x21] = LdIxImm(idx)
            table[0x2A] = LdIxFromAddr(idx)
            table[0x22] = LdAddrFromIx(idx)
            table[0x23] = IncIx(idx)
            table[0x2B] = DecIx(idx)
            table[0xE1] = PopIx(idx)
            table[0xE3] = ExSpIx(idx)
            table[0xE5] = PushIx(idx)
            table[0xF9] = LdSpFromIx(idx)
            // ADD IX,rr — opcodes 09 (BC), 19 (DE), 29 (Self), 39 (SP)
            for (srcBits in 0..3) {
                val opcode = 0x09 or (srcBits shl 4)
                table[opcode] = AddIxPair(idx, srcBits)
            }
        }
    }

    private fun installIndexedLd(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            // LD r,(IX+d) — opcode 46+8*r where r is dst register bits, skip rrr=6 ((HL) which makes no sense here)
            for (dstBits in 0..7) {
                if (dstBits == 6) continue   // 0x76 would be HALT-shape; not LD r,(IX+d)
                val opcode = 0x46 or (dstBits shl 3)
                table[opcode] = LdRegFromIxd(idx, dst = Reg.fromBits(dstBits))
            }
            // LD (IX+d),r — opcode 70+r where r is src register bits, skip rrr=6
            for (srcBits in 0..7) {
                if (srcBits == 6) continue
                val opcode = 0x70 or srcBits
                table[opcode] = LdIxdFromReg(idx, src = Reg.fromBits(srcBits))
            }
        }
    }

    private fun installIndexedLdImm(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            table[0x36] = LdIxdImm(idx)
        }
    }

    private fun installIndexedAlu(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            // ALU A,(IX+d) — opcode 86+8*ooo where ooo is AluOp
            for (oooBits in 0..7) {
                val opcode = 0x86 or (oooBits shl 3)
                table[opcode] = AluAFromIxd(idx, op = AluOp.fromBits(oooBits))
            }
        }
    }

    private fun installIndexedIncDec(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            table[0x34] = IncIxd(idx)
            table[0x35] = DecIxd(idx)
        }
    }

    private fun installJpIx(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.dd else d.fd
            table[0xE9] = JpIx(idx)
        }
    }

    private fun installLdSpHlStraggler(d: Decoder) {
        d.main[0xF9] = LdSpHl
    }
}
```

Wired into `OpTableBuilder.build()` with one `IxOps.installInto(d)` line.

### Test strategy

1. **`IndexReg` enum tests** — read/write per IX/IY, mnemonic.
2. **Per-Op behavioral tests** — state delta + PC/R/T-state assertions; for indexed ops, test with positive and negative displacement.
3. **`IxOpsTest`** asserts each opcode position has the right Op via mnemonic match.
4. Critical assertions: T-state cost matches "HL counterpart + 4"; effective address wraps mod 64K; `ADD IX,IX` reads idx itself, not HL.

## Implementation Sequence

6 work units:

| WU | Subject | Op classes | Opcodes |
|----|---------|------------|---------|
| **2.8-1** | IndexReg enum + IxOps skeleton + wire into builder + LdSpHl straggler | 1 (LdSpHl) + foundation | 1 |
| **2.8-2** | Pair-touching IX/IY ops (10 classes) | 10 | 26 |
| **2.8-3** | LD r,(IX+d) + LD (IX+d),r | 2 | 28 |
| **2.8-4** | LD (IX+d),n + ALU A,(IX+d) + INC/DEC (IX+d) | 4 | 22 |
| **2.8-5** | JP (IX) + JP (IY) | 1 | 2 |
| **2.8-6** | Sweep + tag `m1-phase02-8` | 0 | 0 |

## Risks

- **R1: T-state arithmetic.** Almost every IX/IY op is "HL counterpart + 4". Easy to forget the +4. Per-Op tests assert exact T-states.
- **R2: Signed displacement.** Already-trodden territory; use `mem.read(addr).toByte().toInt()`.
- **R3: ADD IX,IX (and ADD IY,IY)** — the pair encoding's "10" slot means "the index reg itself", NOT HL. `AddIxPair` has a private `readSrc` that maps `srcBits == 2` to `idx.read(cpu)`.
- **R4: Operand byte positioning.** Displacement is at `pc+2` (after prefix at pc+0, opcode at pc+1). For `LD (IX+d),n`: d at pc+2, n at pc+3. Per-Op tests verify.
- **R5: Effective address wraps mod 64K.** Tests with `idx = 0xFFFF, d = 5` → addr = 4.
- **R6: LdSpHl** is the only main-table op in this batch — easy to forget. Foundation WU includes it.
- **R7: `(HL)` / `(IX+d)` distinction in operand positions.** The DD prefix says "treat (HL) as (IX+d)". So opcodes that have `(HL)` in their main-table form become `(IX+d)` when DD-prefixed — but only those whose opcodes contain the bit pattern `110` for the HL slot. For `LD r,r'` (block 0x40-0x7F), DD-prefixed entries with `(HL)` substitution are the indexed LD variants. For `LD (HL),n` (0x36), DD 36 is `LD (IX+d),n`. For ALU A,(HL) (rrr=110), DD 86/8E/96/9E/A6/AE/B6/BE are ALU A,(IX+d). Mitigation: install only the *useful* DD entries; entries that would be no-ops (DD-prefixing an op that doesn't touch HL) are deliberately not installed.
- **R8: `LD r,r'` register-to-register variants under DD prefix** — these would be useless (and their undocumented IXH/IXL behavior is out of scope). Don't install them.

## Done Criteria

1. `./gradlew check` green.
2. opcodes count climbs by ~79.
3. nop_loop still passes.
4. FUSE DD-prefixed cases pass (e.g. DD 21 — LD IX,nn).
5. Tag `m1-phase02-8` placed.

## Open Questions (deferred to implementation)

- Whether `AddIxPair` should validate srcBits in 0..3 in init. Yes — matches `Im(mode)` precedent.
- Whether `AddIxPair`'s `readSrc` private helper should be a method on `IndexReg` or stay file-private. File-private — no other consumers.
