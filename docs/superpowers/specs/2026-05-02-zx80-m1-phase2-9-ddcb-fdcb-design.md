# ZX Spectrum Emulator — Phase 2.9 Design (DDCB/FDCB indexed bit/rotate)

**Date:** 2026-05-02
**Branch:** `opus-4.7` (the de facto main; do not merge to `master`)
**Status:** Approved, ready for implementation planning

## Goal

Implement the documented DDCB/FDCB-prefixed opcodes — indexed (IX+d)/(IY+d) variants of CB-prefixed rotate/shift/BIT/SET/RES. **62 documented opcodes** across 4 Op classes (the other ~450 slots per table are undocumented "copy to r" variants and stay null).

After this plan: opcodes count climbs by 62; FUSE pass-rate ticks up modestly (DDCB/FDCB is a small subset of FUSE).

## Context

Phase 2.7 (planned) covers CB-prefixed rotate/shift/BIT/SET/RES on r/(HL). Phase 2.8 (planned) covers DD/FD-prefixed IX/IY ops including `(IX+d)` addressing for non-bit ops. Phase 2.9 combines those: CB-style operations applied to `(IX+d)` / `(IY+d)` addressing.

The Dispatcher (Phase 2.1a-1) already routes `DD CB d xx` and `FD CB d xx` correctly — verified by the wrap test in DispatcherTest.

Reference top-level spec: `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` (batch 2.9).

## Scope

### In scope

Each instruction is 4 bytes: `DD CB d xx` (or `FD CB d xx`) where `d` is signed 8-bit displacement and `xx` is the operation+target opcode.

Documented opcodes (per DDCB and per FDCB table):

| Slot pattern | Documented count | Op family |
|---|---|---|
| 0x06, 0x0E, 0x16, 0x1E, 0x26, 0x2E, 0x3E (rrr=110, ooo selects 7 documented rotate/shift ops; 0x36 SLL skipped) | 7 | RLC/RRC/RL/RR/SLA/SRA/SRL (IX+d) |
| 0x46, 0x4E, 0x56, 0x5E, 0x66, 0x6E, 0x76, 0x7E (rrr=110, n in 0..7) | 8 | BIT n,(IX+d) |
| 0x86, 0x8E, 0x96, 0x9E, 0xA6, 0xAE, 0xB6, 0xBE | 8 | RES n,(IX+d) |
| 0xC6, 0xCE, 0xD6, 0xDE, 0xE6, 0xEE, 0xF6, 0xFE | 8 | SET n,(IX+d) |

Total per index: **31 documented opcodes**. Across DDCB+FDCB: **62 opcodes**.

The other 225 slots per table (where rrr ≠ 110) are undocumented "copy to r" variants — operation result also written into a named register. Per project non-goals, we leave these null.

### Out of scope

- Undocumented "copy to r" DDCB/FDCB variants.
- DDCB/FDCB SLL (which is rrr=110 + ooo=110, i.e. opcode 0x36) — undocumented per spec non-goals.

## Architecture

### Op classes (4 total, all parameterized over `IndexReg`)

| Class | Opcodes | T-states | PC adv | R increment | Behavior |
|-------|---------|----------|--------|-------------|----------|
| `RotShiftIxd(idx, op)` | 14 (7 RotateOp × 2 idx) | 23 | +4 | +2 | reads d at pc+2; reads byte at idx+d; applies `op.apply(byte, oldF)`; writes result back; updates F |
| `BitIxd(idx, n)` | 16 (8 n × 2 idx) | 20 | +4 | +2 | reads byte at idx+d; tests bit n; Z=!(bit), H=1, N=0, C preserved; memory unchanged |
| `ResIxd(idx, n)` | 16 | 23 | +4 | +2 | reads byte at idx+d; clears bit n; writes back; no flags |
| `SetIxd(idx, n)` | 16 | 23 | +4 | +2 | reads byte at idx+d; sets bit n; writes back; no flags |

All 4 Op classes:
- Read displacement from `mem.read(cpu.pc + 2).toByte().toInt()` (signed).
- Compute effective address as `(idx.read(cpu) + d) and 0xFFFF` (wraps mod 64K).
- `cpu.bumpR(2)` (DD + CB are the 2 M1 cycles; the d byte and opcode are subsequent reads).
- PC += 4.
- Validate construction parameters: `n in 0..7` for BIT/RES/SET.

`operandLength` = 0 for all DDCB ops. (The `d` byte is BEFORE the opcode at pc+3, not after — this field is informational metadata for a future disassembler; the actual PC advance is handled in `execute`.)

### `IxCbOps.installInto(decoder)` fragment

`src/main/kotlin/ru/alepar/zx80/op/ixcb/IxCbOps.kt`. Loops over both DDCB and FDCB tables for each Op family:

```kotlin
object IxCbOps {
    fun installInto(d: Decoder) {
        for (idx in IndexReg.entries) {
            val table = if (idx == IndexReg.IX) d.ddcb else d.fdcb
            installRotateShift(table, idx)
            installBit(table, idx)
            installRes(table, idx)
            installSet(table, idx)
        }
    }

    private fun installRotateShift(table: Array<Op?>, idx: IndexReg) {
        for (oooBits in 0..7) {
            if (oooBits == 6) continue   // SLL — undocumented
            val op = RotateOp.fromBits(oooBits)
            val opcode = (oooBits shl 3) or 0x06   // rrr=110
            table[opcode] = RotShiftIxd(idx, op)
        }
    }

    private fun installBit(table: Array<Op?>, idx: IndexReg) {
        for (n in 0..7) {
            val opcode = 0x40 or (n shl 3) or 0x06
            table[opcode] = BitIxd(idx, n)
        }
    }

    private fun installRes(table: Array<Op?>, idx: IndexReg) {
        for (n in 0..7) {
            val opcode = 0x80 or (n shl 3) or 0x06
            table[opcode] = ResIxd(idx, n)
        }
    }

    private fun installSet(table: Array<Op?>, idx: IndexReg) {
        for (n in 0..7) {
            val opcode = 0xC0 or (n shl 3) or 0x06
            table[opcode] = SetIxd(idx, n)
        }
    }
}
```

Wired into `OpTableBuilder.build()` with one `IxCbOps.installInto(d)` line.

### Test strategy

1. **Per-Op behavioral tests** — state delta + memory read/write at `idx+d` + flag updates per op semantics (rotate ops compute S/Z/PV from result; BIT only reads; RES/SET no flags).
2. **`IxCbOpsTest`** asserts each documented opcode position has the right Op via mnemonic match. Also explicitly asserts undocumented slots (rrr ≠ 110) remain null.
3. Test signed displacement (positive AND negative) for at least one Op per family.
4. Cycle counts: 23T for rotate/SET/RES; 20T for BIT.

## Implementation Sequence

5 work units:

| WU | Subject | Op classes | Opcodes |
|----|---------|------------|---------|
| **2.9-1** | IxCbOps fragment skeleton + wire into builder | 0 | 0 |
| **2.9-2** | RotShiftIxd + register 14 opcodes | 1 | 14 |
| **2.9-3** | BitIxd + register 16 opcodes | 1 | 16 |
| **2.9-4** | ResIxd + SetIxd + register 32 opcodes | 2 | 32 |
| **2.9-5** | Sweep + tag `m1-phase02-9` | 0 | 0 |

## Risks

- **R1: Displacement at pc+2 (BEFORE the opcode at pc+3).** Distinct from CB-only ops where there's no displacement. Mitigation: read with `mem.read(cpu.pc + 2).toByte().toInt()`; verified by Dispatcher's existing wrap test.
- **R2: Effective address wraps mod 64K.** Same as Phase 2.8 — `(idx.read(cpu) + d) and 0xFFFF`.
- **R3: BIT (IX+d) is 20T, not 23T.** Distinct from RES/SET which write back. Per-Op test asserts.
- **R4: Undocumented "copy to r" variants stay null.** ~225 slots per table. `IxCbOpsTest` explicitly asserts a sample of these stays null.
- **R5: Rotate/shift CB-style flag computation differs from rotate-A** (already handled correctly by `RotateOp.apply` which delegates to `Flags.afterRlc`/etc. — those compute S/Z/PV from result). Reuses Phase 2.7's foundation.

## Done Criteria

1. `./gradlew check` green.
2. opcodes count climbs by 62.
3. nop_loop still passes.
4. Tag `m1-phase02-9` placed.
5. The undocumented DDCB/FDCB slots remain null (verified by test).
6. Phase 2.10 (ED-prefixed remainder) is structurally unblocked.

## Open Questions (deferred to implementation)

- Whether `RotShiftIxd`/`BitIxd`/`ResIxd`/`SetIxd` share a tiny private helper for "compute effective address from idx + signed displacement at pc+2". Likely yes — 4 use sites; one-line helper. Decide in WU 2.9-2.
