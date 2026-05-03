# Phase 2.12: Undocumented IX/IY Half-Register Ops + SLL — Design

## Goal

Make `./build/install/zx80/bin/zx80 zexdoc` no longer crash on undocumented
opcodes. After Phase 2.12 lands, ZEXDOC may still emit `ERROR` (CRC
mismatches) due to unmodelled X/Y flag bits — that's a separate phase, not
2.12.

## Context

Phase 2.10 closed the *documented* Z80 ISA. ZEXDOC (the documented-flags
conformance test ROM) currently passes 6 test groups and crashes in group 7
(`aluop a,<ixh,ixl,iyh,iyl>`) at `pc=0x1d42` with
`no dispatch route for opcode 0xdd`. The crash is the test driver patching
`mem[0x1d42] = 0xDD <ixh-alu-opcode>` for which our `Decoder.dd[]` is null.

Reading test-name strings out of `ZEXDOC.COM` directly identified the gaps
that ZEXDOC actually exercises beyond the documented ISA:

| ZEXDOC test group | Status | Gap |
|---|---|---|
| `aluop a,<ixh,ixl,iyh,iyl>` | crash | undocumented IX/IY half-register ALU |
| `<inc,dec> ixh/ixl/iyh/iyl` | will crash | INC/DEC of IX/IY halves |
| `ld <ixh,ixl,iyh,iyl>,nn` | will crash | LD half,immediate |
| `ld <bcdexya>,<bcdexya>` | will crash | LD r,r' under DD/FD prefix where H/L map to IX/IY halves |
| `shf/rot <b,c,d,e,h,l,(hl),a>` | will CRC-fail or crash on SLL | SLL was deliberately left null in Phase 2.7 |

These are the in-scope groups. Other ZEXDOC groups not yet validated (e.g.
`shf/rot (<ix,iy>+1)`, `<set,res> n,(<ix,iy>+1)`) may surface additional
gaps (DDCB undocumented copy-back forms); those are out of scope for this
phase and will be triaged after 2.12 lands.

## Non-goals

- DDCB/FDCB undocumented copy-back forms (rotate/SET/RES with register
  destination as well as memory). If `shf/rot (<ix,iy>+1)` or
  `<set,res> n,(<ix,iy>+1)` ZEXDOC tests reveal these are needed, file as
  Phase 2.13.
- Modelling the X/Y undocumented bits 5 and 3 of the F register. Cross-
  cutting flag-helper rework, separate phase.
- DD-prefix on opcodes outside the 0x40-0x7F LD block where neither operand
  references H/L. Z80 spec says the prefix is a no-op there with +4
  T-states; ZEXDOC does not appear to exercise this case for ALU on
  regular regs (its ALU tests use unprefixed forms or IXH/IXL forms
  specifically). If a crash surfaces here at runtime, scope to a follow-up.

## Architecture

### `IndexHalfReg` enum

New file `src/main/kotlin/ru/alepar/zx80/cpu/IndexHalfReg.kt`:

```kotlin
enum class IndexHalfReg(val mnemonic: String, val parent: IndexReg, val isHigh: Boolean) {
    IXH("IXH", IndexReg.IX, true),
    IXL("IXL", IndexReg.IX, false),
    IYH("IYH", IndexReg.IY, true),
    IYL("IYL", IndexReg.IY, false);

    fun read(cpu: Cpu): Int {
        val full = parent.read(cpu)
        return if (isHigh) (full ushr 8) and 0xFF else full and 0xFF
    }

    fun write(cpu: Cpu, value: Int) {
        val v = value and 0xFF
        val full = parent.read(cpu)
        val composed = if (isHigh) (v shl 8) or (full and 0xFF) else (full and 0xFF00) or v
        parent.write(cpu, composed)
    }
}
```

The DD↔IX-halves and FD↔IY-halves binding is enforced at install time by
the install loops, not by the type system. At runtime the Op is given an
`IndexHalfReg` and just reads/writes through it.

### Op classes

All seven new Op classes live in
`src/main/kotlin/ru/alepar/zx80/op/ix/`. They share the standard
DD/FD-prefixed shape: `pc += 2`, `bumpR(2)`, `baseCycles = 8` (or 11 for
LD half,n), `operandLength = 0` (or 1 for LD half,n).

| Class | Source | Destination | Install positions |
|---|---|---|---|
| `AluAFromIxHalf(op: AluOp, src: IndexHalfReg)` | half | A | 32 (8 ALU × 4 halves) |
| `IncIxHalf(half: IndexHalfReg)` | half | half | 4 |
| `DecIxHalf(half: IndexHalfReg)` | half | half | 4 |
| `LdIxHalfImm(dst: IndexHalfReg)` | n | half | 4 |
| `LdRegRegPrefixed(src: Reg, dst: Reg)` | r | r' | 50 (25 per prefix; same instance reused both prefixes) |
| `LdRegFromIxHalf(dst: Reg, src: IndexHalfReg)` | half | r | 20 (5 dst × 2 halves × 2 prefixes) |
| `LdIxHalfFromReg(dst: IndexHalfReg, src: Reg)` | r | half | 20 (2 halves × 5 src × 2 prefixes) |
| `LdIxHalfFromIxHalf(dst: IndexHalfReg, src: IndexHalfReg)` | half | half | 8 (4 patterns × 2 prefixes) |

**Total new install positions: 142 (excluding SLL); 150 with SLL.**

Breakdown: 32 ALU + 4 INC + 4 DEC + 4 LD half,n + 50 LdRegRegPrefixed + 20
LD r,half + 20 LD half,r + 8 LD half,half + 8 SLL = 150. (98 of those 150
sit in the 0x40-0x7F LD block — 49 per prefix.)

ALU and INC/DEC halves reuse `Flags.afterAdd`, `afterAdc`, `afterInc`, etc.
on the byte read out of the half register. No new flag math.

`LdRegRegPrefixed` has no prefix parameter — DD vs FD doesn't change its
behavior, so the same instance is installed into both `dd[]` and `fd[]`.
Mnemonic is just `"LD B, C"` (the prefix is a no-op).

### SLL extension to Phase 2.7

`Phase 2.7` left CB 0x30-0x37 null with `CbOpsTest` asserting that. Phase
2.12 reverses that decision:

1. Add `SLL` to the existing `RotateOp` enum (alongside RLC/RRC/RL/RR/SLA/
   SRA/SRL).
2. Add the SLL flag-math arm to whichever helper `RotShiftReg` /
   `RotShiftHl` uses (likely `Flags.afterRotateShift` or inline). SLL
   semantics: shift left by 1, set bit 0 to 1, ejected bit 7 → C; flags
   S = bit 7 of result, Z = result == 0, H = 0, N = 0, P/V = parity, C =
   ejected bit.
3. Install 7 `RotShiftReg(SLL, Reg.fromBits(srcBits))` at CB 0x30-0x35,
   0x37 and 1 `RotShiftHl(SLL)` at CB 0x36 — symmetric with how the other
   rotate/shift ops are installed.
4. Replace the negative `CbOpsTest` assertion ("SLL slots null") with
   positive assertions about each slot.

### LD r,r' install loop in IxOps

Existing `IxOps.installInto` is extended (after Phase 2.8's installations)
with a single double-loop covering all 49 patterns × 2 prefixes:

```kotlin
for (prefix in IndexReg.values()) {
    val table = if (prefix == IndexReg.IX) d.dd else d.fd
    for (dstBits in 0..7) {
        for (srcBits in 0..7) {
            val opcode = 0x40 or (dstBits shl 3) or srcBits
            if (opcode == 0x76) continue          // HALT
            if (dstBits == 6 || srcBits == 6) continue  // (IX+d) variants — already done in Phase 2.8 (WU 2.8-3)
            val dstHalf = halfFor(prefix, dstBits)   // IXH/IXL/IYH/IYL or null
            val srcHalf = halfFor(prefix, srcBits)
            table[opcode] = when {
                dstHalf != null && srcHalf != null -> LdIxHalfFromIxHalf(dstHalf, srcHalf)
                dstHalf != null -> LdIxHalfFromReg(dstHalf, Reg.fromBits(srcBits))
                srcHalf != null -> LdRegFromIxHalf(Reg.fromBits(dstBits), srcHalf)
                else -> ldRegRegPrefixed[srcBits][dstBits]   // shared singletons
            }
        }
    }
}
```

`halfFor(prefix, bits)` is an inlined helper: returns `IXH`/`IYH` for
`bits == 4`, `IXL`/`IYL` for `bits == 5`, null otherwise.
`ldRegRegPrefixed` is a precomputed 8x8 array of `LdRegRegPrefixed`
instances reused across both prefixes.

## Validation gates

After Phase 2.12 lands, `./build/install/zx80/bin/zx80 zexdoc` should:

1. **No longer crash** on `aluop a,<ixh,ixl,iyh,iyl>` (group 7).
2. **No longer crash** on `<inc,dec> ixh/ixl/iyh/iyl`.
3. **No longer crash** on `ld <ixh,ixl,iyh,iyl>,nn`.
4. **No longer crash** on `ld <bcdexya>,<bcdexya>`.
5. **No longer crash** on `shf/rot <b,c,d,e,h,l,(hl),a>` due to SLL.

It is acceptable for ZEXDOC to emit `ERROR` (CRC mismatch) on any of these
groups — that is the X/Y flag-bit issue, separate phase. The success
criterion for Phase 2.12 is "ZEXDOC progresses past these test groups
without crashing."

After 2.12, the next dispatch crash (if any) reveals the next undocumented
gap (likely DDCB/FDCB copy-back forms in `shf/rot (<ix,iy>+1)` or
`<set,res> n,(<ix,iy>+1)`); that triages into Phase 2.13 if needed.

Final tag for this phase: `m1-phase02-12`. The `m1-cpu-complete` tag is
NOT applied here — that's gated on a clean ZEXDOC run, which requires
Phase D (X/Y bits) at minimum.

## Test strategy

Per existing Phase 2.8/2.9 conventions:

- Each Op class gets its own test file with 4-5 tests: basic execution,
  flag preservation/computation, mnemonic, baseCycles/operandLength, edge
  case (e.g. wrap, masking).
- `IxOpsTest` extended with installation assertions: spot-check
  representative opcodes per prefix (e.g. `DD 84` = `ADD A, IXH`,
  `DD 60` = `LD IXH, B`, `DD 65` = `LD IXH, IXL`, `DD 78` = `LD A, B`,
  `FD 7C` = `LD A, IYH`); count exactly 49 entries non-null in 0x40-0x7F
  per prefix.
- `CbOpsTest`'s SLL-null assertion is replaced with SLL-installed
  assertions.
- `RotShiftRegTest` and `RotShiftHlTest` get one SLL test each.
- `FlagsTest` gets one test for the new SLL flag arm (or modified existing
  helper).

Estimated new tests: ~50.

## Work-unit breakdown (revises beads zx80-6k8 epic)

After spec is approved, the WUs filed under epic `zx80-6k8` will be
revised to:

1. **WU 2.12-1** (`zx80-cac`, existing) — `IndexHalfReg` enum +
   `AluAFromIxHalf` (32 opcodes). 16 tests + 4 enum tests.
2. **WU 2.12-2** (`zx80-aq4`, existing) — `IncIxHalf` + `DecIxHalf` (8
   opcodes). 8 tests.
3. **WU 2.12-3** (`zx80-yta`, existing) — `LdIxHalfImm` (4 opcodes). 4
   tests.
4. **WU 2.12-4** (`zx80-ll3`, existing) — `LdRegRegPrefixed` +
   `LdRegFromIxHalf` + `LdIxHalfFromReg` + `LdIxHalfFromIxHalf` (98
   install positions). 16 tests + IxOps coverage assertions.
5. **WU 2.12-5** (NEW, replaces existing zx80-019 conditional DDCB-
   copyback) — Add SLL to RotateOp + install CB 0x30-0x37 (8 opcodes). 5
   tests.
6. **WU 2.12-6** (`zx80-y8k`, existing) — Sweep: re-run ZEXDOC, confirm no
   crash through validation gates above, tag `m1-phase02-12`. Triage any
   newly-surfaced ZEXDOC failures into Phase 2.13 issues if dispatch crashes
   (out-of-scope for sweep WU).

Existing `zx80-019` (conditional DDCB copy-back WU) becomes superseded by
the NEW WU 2.12-5 (SLL); its description is rewritten or it is closed and
a fresh issue created.

`zx80-jfb` (Phase 2.11 WU 7 — m1-cpu-complete tag) remains blocked by
`zx80-y8k`. After Phase 2.12, the path forward depends on whether
ZEXDOC's remaining failures are crashes (Phase 2.13 work) or just CRC
mismatches (Phase D — X/Y bits — which is what `zx80-jfb` legitimately
covers).

## Risks

- **Phase 2.7 RotShiftReg parameterization.** Spec assumes
  `RotShiftReg(RotateOp, Reg)` style. If actual code has per-op classes
  instead, WU 2.12-5 needs a small refactor first. The plan WU will read
  Phase 2.7 code first and adapt.
- **DD prefix on non-LD-block, non-half opcodes.** If ZEXDOC turns out
  to exercise these (e.g. `DD 80` = ADD A,B with prefix), they're not
  covered. Surfacing this is by runtime crash; scope to follow-up if it
  happens.
- **`LdRegRegPrefixed` shared instance.** If we later need to distinguish
  DD from FD for any reason (disassembler, debug trace), the shared
  instance approach needs revisiting. For M1 it's fine.
