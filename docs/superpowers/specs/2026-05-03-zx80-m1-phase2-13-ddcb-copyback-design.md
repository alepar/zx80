# Phase 2.13: DDCB/FDCB Undocumented Copy-back Forms — Design

## Goal

Make `./build/install/zx80/bin/zx80 zexdoc` run to completion. After Phase
2.13 lands, if ZEXDOC produces no `ERROR ****` lines, tag
`m1-cpu-complete`. If it does, file Phase D for the remaining flag-edge
work.

## Context

After Phase 2.12, ZEXDOC progresses through 59 test groups (5 of them
exercising newly-added IXH/IXL/IYH/IYL ops) with **zero CRC errors**, then
crashes at `shf/rot (<ix,iy>+1)` with the same `no dispatch route for
opcode 0xdd at pc=0x1d42` symptom — the test driver patching in an
undocumented DDCB-prefixed copy-back instruction.

The DDCB/FDCB tables have 256 slots each. Phase 2.9 installed only the
documented `rrr=110` slots (62 entries per prefix: 7 rotate + 8 BIT + 8
SET + 8 RES) plus skipped SLL entirely. The other 194 slots per prefix
are null. ZEXDOC patches in those undocumented copy-back forms during
the `shf/rot (<ix,iy>+1)` and `<set,res> n,(<ix,iy>+1)` test groups.

## Z80 quirk: undocumented copy-back semantics

The 4 functional families in DDCB/FDCB encode 256 slots each:
- `0x00-0x3F` — rotate/shift (`ooo`-bits = 8 ops, `rrr`-bits = 8 register positions)
- `0x40-0x7F` — `BIT n` (`nnn`-bits = 8 bit positions, `rrr` = 8 positions)
- `0x80-0xBF` — `RES n`
- `0xC0-0xFF` — `SET n`

For each (op, n), `rrr=110` is the documented form: read memory[IX+d],
apply, write memory[IX+d] back. The 7 sibling slots (`rrr ∈ {0..5,7}`)
are undocumented and behave as:

- **rotate/shift copy-back** (e.g. `RLC (IX+d), B`): read mem[IX+d],
  apply rotate, write to mem[IX+d] AND copy result to register r.
- **SET/RES copy-back** (e.g. `SET 3, (IX+d), B`): read mem[IX+d], apply
  bit op, write to mem[IX+d] AND copy result to register r.
- **BIT undocumented mirror** (e.g. `BIT 3, (IX+d), B`): identical to the
  documented form (BIT has no result and the register field is ignored).
  ZEXDOC's `bit n,(<ix,iy>+1)` test passed OK in the prior run, so we do
  NOT install these mirror slots — out of scope.

## Scope

**In scope:**

- 3 new Op classes:
  - `RotShiftIxdCopyback(idx: IndexReg, op: RotateOp, dst: Reg)` — 112
    install positions (8 rotate ops × 7 register dsts × 2 prefixes).
    Includes SLL since Phase 2.12 already added SLL to `RotateOp`.
  - `ResIxdCopyback(idx: IndexReg, n: Int, dst: Reg)` — 112 install
    positions.
  - `SetIxdCopyback(idx: IndexReg, n: Int, dst: Reg)` — 112 install
    positions.

- Remove the `if (oooBits == 6) continue // SLL — undocumented` skip in
  `IxCbOps.installRotateShift`. The slot at DDCB/FDCB `0x36` is now
  `RotShiftIxd(idx, RotateOp.SLL)` — using the existing class. **2 install
  positions added** by removing this skip.

- Extend `IxCbOps.installInto` with three new install methods
  (`installRotateShiftCopyback`, `installResCopyback`,
  `installSetCopyback`).

**Out of scope:**

- BIT undocumented mirror (rrr ∈ {0..5,7} for BIT). ZEXDOC's existing
  `bit n,(<ix,iy>+1)` ran OK without it.
- Phase D: modelling X/Y undocumented bits 5 and 3 of F. Whether this is
  needed at all depends on whether ZEXDOC produces CRC errors after
  Phase 2.13 — recent evidence (59 ZEXDOC groups OK, zero CRC errors)
  suggests it may not be needed for `m1-cpu-complete`.

**Total install positions added: 338** (112 + 2 + 112 + 112).

## Architecture

### `RotShiftIxdCopyback`

```kotlin
class RotShiftIxdCopyback(
    private val idx: IndexReg,
    private val op: RotateOp,
    private val dst: Reg,
) : Op {
    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val r = op.apply(mem.read(addr), cpu.f)
        mem.write(addr, r.value)
        dst.write(cpu, r.value)         // ← copy-back step
        cpu.f = r.newF
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) =
        "${op.mnemonic} (${idx.mnemonic}+d), ${dst.mnemonic}"
}
```

### `ResIxdCopyback` and `SetIxdCopyback`

Same shape, but the operation is `read & ~mask` (RES) or `read | mask`
(SET) where `mask = 1 shl n`. No flag changes. Both write the result to
memory AND to the register.

```kotlin
class ResIxdCopyback(
    private val idx: IndexReg,
    private val n: Int,
    private val dst: Reg,
) : Op {
    override val operandLength = 0
    override val baseCycles = 23

    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        val result = mem.read(addr) and (0xFF xor (1 shl n))
        mem.write(addr, result)
        dst.write(cpu, result)
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }

    override fun mnemonic(operands: OperandFetcher) =
        "RES $n, (${idx.mnemonic}+d), ${dst.mnemonic}"
}
```

`SetIxdCopyback` is identical with `or (1 shl n)` instead of `and (xor)`.

### Updated `IxCbOps.installInto`

```kotlin
fun installInto(d: Decoder) {
    for (idx in IndexReg.entries) {
        val table = if (idx == IndexReg.IX) d.ddcb else d.fdcb
        installRotateShift(table, idx)        // EXISTING — oooBits=6 skip removed
        installRotateShiftCopyback(table, idx) // NEW
        installBit(table, idx)                // EXISTING
        installRes(table, idx)
        installResCopyback(table, idx)        // NEW
        installSet(table, idx)
        installSetCopyback(table, idx)        // NEW
    }
}

private fun installRotateShift(table: Array<Op?>, idx: IndexReg) {
    for (oooBits in 0..7) {
        // Phase 2.13: oooBits=6 (SLL) is now installed (Phase 2.12 added SLL to RotateOp).
        val op = RotateOp.fromBits(oooBits)
        val opcode = (oooBits shl 3) or 0x06
        table[opcode] = RotShiftIxd(idx, op)
    }
}

private fun installRotateShiftCopyback(table: Array<Op?>, idx: IndexReg) {
    for (oooBits in 0..7) {
        val op = RotateOp.fromBits(oooBits)
        for (rrrBits in 0..7) {
            if (rrrBits == 6) continue // documented memory-only form, handled above
            val opcode = (oooBits shl 3) or rrrBits
            table[opcode] = RotShiftIxdCopyback(idx, op, Reg.fromBits(rrrBits))
        }
    }
}

private fun installResCopyback(table: Array<Op?>, idx: IndexReg) {
    for (n in 0..7) {
        for (rrrBits in 0..7) {
            if (rrrBits == 6) continue
            val opcode = 0x80 or (n shl 3) or rrrBits
            table[opcode] = ResIxdCopyback(idx, n, Reg.fromBits(rrrBits))
        }
    }
}

private fun installSetCopyback(table: Array<Op?>, idx: IndexReg) {
    for (n in 0..7) {
        for (rrrBits in 0..7) {
            if (rrrBits == 6) continue
            val opcode = 0xC0 or (n shl 3) or rrrBits
            table[opcode] = SetIxdCopyback(idx, n, Reg.fromBits(rrrBits))
        }
    }
}
```

## Test strategy

Per existing project conventions:

- Each Op class gets its own test file (~4-5 tests): basic execute,
  register-copyback verification, mnemonic, baseCycles/operandLength,
  edge case (e.g. negative displacement).
- `IxCbOpsTest` extended with:
  - SLL on indexed: positive assertions at DDCB/FDCB `0x36` =
    `RotShiftIxd(_, SLL)`.
  - Spot-check assertions for ~5 representative copyback positions per
    family per prefix (e.g. DDCB `0x00` = `RLC (IX+d), B`; DDCB `0x80` =
    `RES 0, (IX+d), B`; DDCB `0xCF` = `SET 1, (IX+d), A`).
  - Count assertion: each of `d.ddcb` and `d.fdcb` has exactly 256
    non-null entries (full coverage of the table).

Estimated new tests: ~25.

## Validation gates

After Phase 2.13 lands, the WU 2.13-4 sweep runs:

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. `timeout 600 ./build/install/zx80/bin/zx80 zexdoc 2>&1 | tee /tmp/zexdoc.log`
3. Verify `/tmp/zexdoc.log` contains `Tests complete`.
4. Verify `/tmp/zexdoc.log` contains no `IllegalStateException` lines.
5. Count `ERROR ****` lines:
   - **If 0:** apply `git tag -a m1-phase02-13` AND `git tag -a m1-cpu-complete`. Close `zx80-jfb` (Phase 2.11 WU 7) and `zx80-949` (Phase 2.11 epic). M1 CPU is done.
   - **If > 0:** apply `git tag -a m1-phase02-13`, file Phase D for X/Y flag bits, leave `zx80-jfb` open blocked by Phase D.

## Work-unit breakdown (filed under `zx80-bws` epic)

| WU | Description |
|---|---|
| 2.13-1 | RotShiftIxdCopyback (112 ops) + SLL on indexed (2 ops via removing the skip in `installRotateShift`). |
| 2.13-2 | ResIxdCopyback (112 ops). |
| 2.13-3 | SetIxdCopyback (112 ops). |
| 2.13-4 | Sweep: full suite, ZEXDOC clean run, tag `m1-phase02-13`, conditionally tag `m1-cpu-complete` and close M1 epic. |

Within-phase deps: 2.13-1, 2.13-2, 2.13-3 are independent (different
files, different table regions) but all touch `IxCbOps.kt`; a subagent
runs them sequentially. 2.13-4 depends on all three.

Cross-phase deps: `zx80-jfb` already depends on `zx80-bws`. After
WU 2.13-4 closes, `zx80-jfb` becomes ready (or stays blocked-by-Phase-D
if CRCs fail).

## Risks

- **More ZEXDOC test groups beyond what the strings list shows.** ZEXDOC's
  `Tests complete` line is the success marker. If we don't see it,
  another gap has surfaced — handle via a follow-up phase.
- **CRC errors after Phase 2.13.** The X/Y flag-bit assumption may turn
  out to be needed after all. The Phase 2.13 sweep WU has a clear
  decision rule (errors > 0 → file Phase D, don't tag
  `m1-cpu-complete`).
- **T-state correctness.** Plan uses 23 T-states for both rotate and
  SET/RES copyback — same as the documented memory-only forms. ZEXDOC
  doesn't verify T-states (it tests state and flags), so even if 23 is
  off by a cycle, ZEXDOC will pass. Document the 23 estimate as
  reasonable; if a future test suite verifies T-states, audit.
