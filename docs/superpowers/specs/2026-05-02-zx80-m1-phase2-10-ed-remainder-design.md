# ZX Spectrum Emulator — Phase 2.10 Design (ED-prefixed remainder + I/O + Trap B fix)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Implement the remaining ED-prefixed opcodes (block ops, special LDs, NEG, RETI/RETN, RRD/RLD, I/O), the two main-table I/O stragglers (IN A,(n) and OUT (n),A), and two harness foundations: an `IoBus` abstraction (no-op default for M1) and the **Trap B fix** that lets FuseSuite honor the `tStatesToRun` field for block ops. **~51 documented opcodes across ~28 Op classes.**

After this plan: opcodes count climbs by ~51; FUSE pass-rate jumps significantly because (a) all the previously-failing block-op cases now run correctly thanks to Trap B fix, and (b) ED-prefixed cases get their final coverage. Composite score moves to its M1 plateau.

## Context

Phases 2.1a–2.9 (planned) cover all main-table opcodes plus CB, DD/FD, DDCB/FDCB. Phase 2.10 is the last opcode batch — once it lands, the documented Z80 instruction set is complete.

Two architectural pieces have been deferred to this batch by prior plan reviews:
- **IoBus abstraction.** Spec section says `IN`/`OUT` ops in M1 use a no-op bus returning 0xFF on read and ignoring writes. Foundation needed before any I/O op can be implemented.
- **Trap B fix.** FuseSuite currently runs exactly one instruction per case; block ops (LDIR etc.) loop in place by decrementing PC, so a single step only does one iteration. Fix: loop until `cpu.tStates >= startTStates + input.tStatesToRun`. Non-block ops (the vast majority) still run exactly once because their cost matches tStatesToRun.

Reference top-level spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` (batch 2.10).

## Scope

### In scope

| Group | Opcodes | Count |
|-------|---------|-------|
| Register transfers | LD I,A (ED 47), LD A,I (ED 57), LD R,A (ED 4F), LD A,R (ED 5F) | 4 |
| NEG | ED 44 | 1 |
| Returns | RETI (ED 4D), RETN (ED 45) | 2 |
| BCD nibble rotate | RRD (ED 67), RLD (ED 6F) | 2 |
| Extended LD pair to memory | LD (nn),BC/DE/HL/SP at ED 43/53/63/73 | 4 |
| Extended LD pair from memory | LD BC/DE/HL/SP,(nn) at ED 4B/5B/6B/7B | 4 |
| Block move | LDI (ED A0), LDD (ED A8), LDIR (ED B0), LDDR (ED B8) | 4 |
| Block compare | CPI (ED A1), CPD (ED A9), CPIR (ED B1), CPDR (ED B9) | 4 |
| Block input | INI (ED A2), IND (ED AA), INIR (ED B2), INDR (ED BA) | 4 |
| Block output | OUTI (ED A3), OUTD (ED AB), OTIR (ED B3), OTDR (ED BB) | 4 |
| Single I/O (ED) | IN r,(C) at ED 40+8*r (8 ops), OUT (C),r at ED 41+8*r (8 ops) | 16 |
| Single I/O (main) stragglers | IN A,(n) at 0xDB, OUT (n),A at 0xD3 | 2 |

Total: **~51 documented opcodes**.

Note that ED 63 (LD (nn),HL) and ED 6B (LD HL,(nn)) duplicate the main-table 0x22/0x2A. Both encodings are documented and FUSE tests them; we install both. The ED variants take 20 T-states (vs 16 for the main-table HL forms — 4-cycle prefix penalty).

Plus foundations: `IoBus` interface + `NoIoBus` singleton + `Cpu.io` field + Trap B fix in `FuseSuite.runOne` + `EdOps.installInto(decoder)` fragment.

### Out of scope (deferred)

- Memory contention timing — non-goal.
- Undocumented ED opcodes (most of which behave as NOPs with prefix penalty; we leave their slots null).
- Real I/O hardware (ULA, keyboard, beeper) — M2.

## Architecture

### `IoBus` interface

`src/main/kotlin/ru/alepar/zx80/cpu/IoBus.kt`:

```kotlin
package ru.alepar.zx80.cpu

/**
 * Z80 I/O port bus. Ports are 16-bit (0..0xFFFF) on the Z80;
 * IN A,(n) sets high byte from A, IN r,(C) uses BC.
 *
 * In M1 we use a no-op default (NoIoBus) returning 0xFF on read and
 * ignoring writes. M2 will swap in a real bus connected to ULA,
 * keyboard, etc.
 */
interface IoBus {
    fun read(port: Int): Int
    fun write(port: Int, value: Int)
}

object NoIoBus : IoBus {
    override fun read(port: Int): Int = 0xFF
    override fun write(port: Int, value: Int) { /* ignore */ }
}
```

Add to `Cpu`:

```kotlin
class Cpu {
    // ... existing fields ...
    var io: IoBus = NoIoBus
}
```

I/O Op classes use `cpu.io.read(port)` and `cpu.io.write(port, value)`.

### Trap B fix in `FuseSuite.runOne`

Current code (one-shot dispatch):

```kotlin
val op = dispatcher.decodeAt(cpu, mem) ?: return "no op for opcode 0x... (no dispatch route)"
op.execute(cpu, mem)
```

New code (loop until tStatesToRun reached):

```kotlin
val startTStates = cpu.tStates
val targetTStates = startTStates + input.tStatesToRun
while (cpu.tStates < targetTStates) {
    val curOp = dispatcher.decodeAt(cpu, mem) ?: run {
        val opcodeByte = mem.read(cpu.pc)
        return "no op for opcode 0x${"%02X".format(opcodeByte)} (no dispatch route)"
    }
    curOp.execute(cpu, mem)
}
```

For non-block ops: tStatesToRun equals the op's documented cost, so one iteration suffices. For LDIR/LDDR/CPIR/CPDR/INIR/INDR/OTIR/OTDR: the loop runs N iterations until BC=0 or until the harness budget is exhausted (whichever comes first).

### Op class shapes

All ED-prefixed Op classes:
- `cpu.bumpR(2)` (ED + opcode = 2 M1 cycles)
- PC advance varies (most are +2; some are +4 for ED nn nn forms)
- T-states per documented spec

#### Register transfers (4 classes)

- `LdIA`: ED 47, 9T, PC+=2. `cpu.i = cpu.a`. No flags.
- `LdRA`: ED 4F, 9T, PC+=2. `cpu.r = cpu.a` (sets all 8 bits — note this is a write, not the usual bumpR-style increment). No flags.
- `LdAI`: ED 57, 9T, PC+=2. `cpu.a = cpu.i`. **Flags computed:** S=bit7 of A, Z=A==0, H=0, **P/V=cpu.iff2**, N=0, C preserved.
- `LdAR`: ED 5F, 9T, PC+=2. `cpu.a = cpu.r`. Same flag rules as LdAI.

#### NEG (1 class)

- `Neg`: ED 44, 8T, PC+=2. `A = (0 - A) & 0xFF`. Computes flags as if `Flags.afterSub(0, oldA, 0)`. Specifically: N=1, C=1 if oldA != 0 else 0, P/V=1 if oldA == 0x80 else 0, S/Z/H from result.

Implementation: delegates to `Flags.afterSub(0, cpu.a, 0)`.

#### Returns (2 classes)

- `Reti`: ED 4D, 14T, PC+=2 (then pop). `cpu.pc = cpu.pop(mem)`. No flag changes. (RETI signals end-of-interrupt to peripherals on real Z80; in our M1 emulator with no peripherals it behaves identically to RET except for the prefix overhead.)
- `Retn`: ED 45, 14T, PC+=2 (then pop). `cpu.pc = cpu.pop(mem); cpu.iff1 = cpu.iff2`. Restores IFF1 from IFF2 — the difference from RET.

#### BCD nibble rotate (2 classes)

- `Rrd`: ED 67, 18T, PC+=2.
  - Read mem[HL] = m.
  - newM = ((A and 0x0F) shl 4) or (m ushr 4)
  - newA = (A and 0xF0) or (m and 0x0F)
  - Write newM to mem[HL]; A = newA.
  - Flags: S/Z from new A, P/V=parity, H=0, N=0, C preserved.
- `Rld`: ED 6F, 18T, PC+=2. Similar but rotation is the other direction.

#### Extended LD pair to/from memory (2 parameterized classes)

- `LdAddrFromPair(pair)`: ED 43/53/63/73, 20T, PC+=4, operandLength=2. `mem.writeWord(addr, pair.read(cpu))` where addr is at pc+2..pc+3. No flags.
- `LdPairFromAddr(pair)`: ED 4B/5B/6B/7B, 20T, PC+=4, operandLength=2. `pair.write(cpu, mem.readWord(addr))`. No flags.

Both parameterized over `RegPair` (BC/DE/HL/SP) using existing `RegPair.fromBits` mapping.

#### Block move (4 classes)

All update flags (specific rules per op).

- `Ldi`: ED A0, 16T, PC+=2.
  - `mem.write(cpu.de, mem.read(cpu.hl))`
  - HL += 1 (mod 64K), DE += 1, BC -= 1
  - Flags: H=0, N=0, P/V = (BC ≠ 0 after), S/Z preserved (per spec; XF/YF undocumented and skipped). C preserved.
- `Ldd`: ED A8, 16T. Same as Ldi but HL/DE decrement instead.
- `Ldir`: ED B0. Same as Ldi but if BC ≠ 0 after, **decrement PC by 2 so the next dispatch re-fires this op**, and use 21T instead of 16T.
- `Lddr`: ED B8. Same as Ldd with the loop.

Implementation strategy: each Op handles exactly one iteration. The Trap B fix in FuseSuite runs the loop multiple times until tStates is reached.

```kotlin
override fun execute(cpu: Cpu, mem: Memory) {
    mem.write(cpu.de, mem.read(cpu.hl))
    cpu.hl = (cpu.hl + 1) and 0xFFFF
    cpu.de = (cpu.de + 1) and 0xFFFF
    cpu.bc = (cpu.bc - 1) and 0xFFFF
    cpu.f = computeLdiFlags(cpu.f, cpu.bc != 0)
    if (this == Ldir && cpu.bc != 0) {
        cpu.pc = (cpu.pc - 0) and 0xFFFF   // PC was already at LDIR start; we leave it
        // Wait — we need to think. Current convention: Op.execute advances PC.
        // For LDIR: advance PC normally (+2), but if BC ≠ 0 walk back by 2.
        cpu.pc = (cpu.pc + 2 - 2) and 0xFFFF   // i.e., PC unchanged
        cpu.tStates += 21
    } else {
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.tStates += if (this == Ldir) 16 else 16
    }
    cpu.bumpR(2)
}
```

Cleaner shape — let each block op do the iteration and leave PC unchanged when looping (since we never advanced it past the op):

```kotlin
override fun execute(cpu: Cpu, mem: Memory) {
    // Body work
    if (loopable && cpu.bc != 0) {
        // Don't advance PC — next dispatch re-fires this op
        cpu.tStates += loopCycles
    } else {
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.tStates += exitCycles
    }
    cpu.bumpR(2)
}
```

#### Block compare (4 classes)

- `Cpi`: ED A1, 16T. Compares A with mem[HL]; sets flags like CP A,(HL) but without writing A. Then HL += 1, BC -= 1.
- `Cpd`: ED A9. HL -= 1.
- `Cpir`: ED B1. Loop; exits when BC=0 OR when match found.
- `Cpdr`: ED B9. Loop variant of Cpd.

#### Block I/O (8 classes)

- `Ini`: ED A2, 16T. Reads byte from `cpu.io.read(cpu.bc)`, writes to mem[HL]. HL += 1, B -= 1. Flags computed.
- `Ind`, `Inir`, `Indr`: variants.
- `Outi`: ED A3, 16T. Reads byte from mem[HL], writes to `cpu.io.write(cpu.bc, byte)`. HL += 1, B -= 1.
- `Outd`, `Otir`, `Otdr`: variants.

(Block I/O flag rules are documented but rarely-exercised; we implement the standard subset and accept that some FUSE undocumented-flag cases may not pass.)

#### Single I/O (3 classes parameterized + 2 singletons)

- `InRC(dst)`: ED 40+8*r, 12T, PC+=2. Reads `cpu.io.read(cpu.bc)`, writes to register `dst`. For dst encoded as rrr=110, the result goes nowhere (only flags update). Flags: S/Z/PV from byte read, H=0, N=0, C preserved.
- `OutCR(src)`: ED 41+8*r, 12T, PC+=2. Writes `src.read(cpu)` to `cpu.io.write(cpu.bc, ...)`. For rrr=110, writes 0 (or undefined; we write 0). No flags.
- `InAImm`: 0xDB nn, 11T, PC+=2, operandLength=1 (main table). port = (cpu.a shl 8) or n. cpu.a = cpu.io.read(port). No flags.
- `OutImmA`: 0xD3 nn, 11T, PC+=2, operandLength=1 (main table). port = (cpu.a shl 8) or n. cpu.io.write(port, cpu.a). No flags.

### `EdOps.installInto(decoder)` fragment

`src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt`. Combines all ED + main-table-straggler installations:

```kotlin
object EdOps {
    fun installInto(d: Decoder) {
        installRegisterTransfers(d)
        installNeg(d)
        installReturns(d)
        installRrdRld(d)
        installExtendedLdPair(d)
        installBlockMove(d)
        installBlockCompare(d)
        installBlockIo(d)
        installSingleIo(d)
        installMainTableIoStragglers(d)
    }
    // ... per-installer methods ...
}
```

Wired into `OpTableBuilder.build()` with one `EdOps.installInto(d)` line.

### Test strategy

1. **Per-Op behavioral tests** with state delta + cycle assertions.
2. **Block op tests run multiple iterations** to verify the loop semantics (BC=2 → 2 iterations of LDIR; BC=0 → exit immediately).
3. **Trap B fix verification** in `FuseSuiteTest`: synthetic case with tStatesToRun > one op's cost runs multiple instructions.
4. **`IoBus` tests**: NoIoBus returns 0xFF on read; verify InAImm picks up the 0xFF.
5. **`EdOpsTest`** asserts each documented opcode position has the right Op via mnemonic match.

## Implementation Sequence

7 work units:

| WU | Subject | Op classes | Opcodes |
|----|---------|------------|---------|
| **2.10-1** | IoBus + NoIoBus + Cpu.io field + Trap B fix in FuseSuite + EdOps fragment skeleton + wire into builder | 0 | 0 |
| **2.10-2** | Register transfers (LD I,A etc.) + NEG + RETI + RETN + RRD + RLD | 8 | 8 |
| **2.10-3** | Extended LD rr,(nn) and LD (nn),rr (2 parameterized classes, 8 opcodes) | 2 | 8 |
| **2.10-4** | Block move (LDI/LDD/LDIR/LDDR) — first WU exercising Trap B fix in real ops | 4 | 4 |
| **2.10-5** | Block compare (CPI/CPD/CPIR/CPDR) | 4 | 4 |
| **2.10-6** | I/O — IN A,(n), OUT (n),A, IN r,(C), OUT (C),r, INI/IND/INIR/INDR, OUTI/OUTD/OTIR/OTDR | 12 | 18 |
| **2.10-7** | Sweep + tag `m1-phase02-10` | 0 | 0 |

## Risks

- **R1: LDIR/LDDR PC handling.** The convention (PC unchanged on loop, advanced on exit) needs to be consistent and well-documented. Mitigation: per-Op test runs 2 iterations manually (calls execute() twice) and verifies PC behavior after each.
- **R2: Trap B fix could break non-block tests.** Shouldn't (single-step ops consume exactly tStatesToRun), but worth verifying. Mitigation: re-run all FUSE cases after the fix; flag any regressions.
- **R3: P/V for LD A,I and LD A,R = IFF2.** Easy to forget; default flag computation would use parity. Mitigation: explicit test sets cpu.iff2=true and asserts P/V is set after LD A,I.
- **R4: NEG is `0 - A`, not `~A + 1` directly.** Implementation can use `Flags.afterSub(0, cpu.a, 0)` for correctness.
- **R5: Z80 I/O port is 16-bit.** `IN r,(C)` uses `cpu.bc` as port (B is high byte). `IN A,(n)` uses `(cpu.a shl 8) or n` as port. Easy to model wrong.
- **R6: RRD/RLD nibble rotation direction.** RRD: A's low nibble → (HL)'s high nibble; (HL)'s high → (HL)'s low; (HL)'s low → A's low. Easy to get the cycle wrong.
- **R7: RETN restores IFF1 from IFF2** (RETI doesn't). Mitigation: explicit test.
- **R8: ED 63 and ED 6B are duplicates of main 0x22 and 0x2A.** Both must be installed (FUSE tests both encodings). Different cycle counts (20T vs 16T).
- **R9: Block I/O flag computation has documented but hairy rules.** We implement the standard subset; some undocumented FUSE-expected flag bits won't be set. Accept the partial pass rate.

## Done Criteria

1. `./gradlew check` green.
2. opcodes count climbs by ~51.
3. nop_loop still passes.
4. Block op FUSE cases pass — verifies Trap B fix works end-to-end.
5. Tag `m1-phase02-10` placed.
6. After this batch, the documented Z80 instruction set is essentially complete.

## Open Questions (deferred to implementation)

- Whether the 4 register-transfer ops should be parameterized over a `SpecialReg` enum (I, R) — saves 2 classes but the asymmetric flag rules (LD A,I/R compute flags but LD I/R,A don't) would muddle the parameterization. Decide in WU 2.10-2.
- Whether block move + block compare share a small "step direction" enum (Inc/Dec). Likely overengineered — separate classes match Phase 2.2's per-op pattern.
- I/O port handling for `(C)` ops: spec says 16-bit BC. Verify with FUSE.
