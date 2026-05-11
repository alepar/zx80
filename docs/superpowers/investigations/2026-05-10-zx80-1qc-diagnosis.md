# zx80-1qc Diagnosis: ROM "stuck at 0x11E6"

**Investigator:** Claude Sonnet 4.6  
**Date:** 2026-05-10  
**Result:** Hard stop — no buggy Op found; gate specification is incorrect

---

## Pre-check Outcome

Pre-check FAILED: `mem.read(0x5C78)` after 50 frames = 0 (not 48-52). The M1 residuals
fix (LD A,R bumpR ordering) has no effect on the ROM init — `LD A,R` (ED 5F) does not
appear anywhere in the 48K ROM.

---

## The Two ROM Init Loops

The ROM boot path: `0x0000 DI; XOR A; LD DE,0xFFFF; JP 0x11CB`. At 0x11CB: border
set, I=0x3F, HL=DE=0xFFFF. Then:

### Loop 1 — Memory fill (0x11DC-0x11E1)

```
0x11DC: LD (HL), 0x02    ; fill address with sentinel 2
0x11DE: DEC HL           ; step down
0x11DF: CP A, H          ; compare A=0x3F with H
0x11E0: JR NZ, 0xFA      ; repeat while H != 0x3F
```

Fills 0xFFFF..0x3FFF (49,153 addresses) with the value 2.  
Writes to ROM area (0x3F00-0x3FFF) are silently dropped (WritePolicy).  
Exit: HL = 0x3FFF (H first equals A=0x3F after decrement from 0x4000).  
**Correct behavior. ~1.58M T-states (32 T-states × 49,152 iters + 27).**

### Loop 2 — RAMTOP scan (0x11E2-0x11EE)

```
0x11E2: AND A, A         ; clear carry
0x11E3: SBC HL, DE       ; HL = HL - 0xFFFF (with C=0) = HL+1 with borrow -> C=1
0x11E5: ADD HL, DE       ; HL = (HL+1) + 0xFFFF = HL, carry still 1
0x11E6: INC HL           ; net: HL += 1 per iteration
0x11E7: JR NC, +6        ; not taken (C always 1 during RAM scan)
0x11E9: DEC (HL)         ; in RAM: 2->1, Z=0. In ROM: value unchanged, Z based on ROM byte
0x11EA: JR Z, +3         ; exit to 0x11EF if first DEC gave 0 (ROM byte was 1)
0x11EC: DEC (HL)         ; in RAM: 1->0, Z=1. In ROM: same as first (write dropped)
0x11ED: JR Z, 0xF3       ; -> 0x11E2 if Z=1 (RAM confirmed writable); else fall through
```

Scans 0x4000..0xFFFF (49,152 RAM locs, all filled with 2 by loop 1) and continues
into 0x0000 (ROM). First ROM address with byte != 1 causes Z=0 on both DECs; falls
through to 0x11EF. In practice exits at 0x0001 (ROM byte 0xAF, DEC->0xAE, Z=0).  
**Correct behavior. ~4.13M T-states (84 T-states × 49,152 RAM iters + exit).**

---

## Root Cause of "stuck at 0x11E6"

There is no infinite loop. The CPU is NOT stuck; it is executing loop 2 at ~84
T-states per iteration across 49,152 RAM addresses. After 50 frames (3,494,400
T-states), the CPU is typically around HL=0xDF00 — deep in loop 2 with ~26K
iterations remaining.

The ROM reaches EI at **5,705,613 T-states = 81.6 frames** from reset. After that,
50 ISR calls accumulate over another 50 frames, making `mem.read(0x5C78) = 50` at
frame **~132**.

The "ROM stuck at 0x11E6" description refers to observing PC=0x11E6 (INC HL, inside
loop 2) when checking after 50 frames. The ROM is NOT stuck — it is making progress
but the gate fires too early.

---

## Gate Specification Error

The 50-frame gate (`mem.read(0x5C78)` in [48,52] after 50 frames) is incorrect for a
correct ZX Spectrum 48K emulator. The real Spectrum 48K ROM init takes approximately
2 seconds at 3.5 MHz = ~100 frames. Our trace confirms ~82 frames for EI plus 50
more frames for 50 ISR calls = **132 frames needed**.

A correct gate should be: run 132+ frames, then assert `mem.read(0x5C78)` in [48,52].

---

## All Loop Instructions Verified Correct

| Address | Op         | FUSE-verified | T-states |
|---------|------------|--------------|----------|
| 0x11DC  | LD (HL), n | yes          | 10       |
| 0x11DE  | DEC HL     | yes          | 6        |
| 0x11DF  | CP H       | yes (afterCp)| 4        |
| 0x11E0  | JR NZ      | yes          | 12/7     |
| 0x11E2  | AND A      | yes          | 4        |
| 0x11E3  | SBC HL, DE | yes          | 15       |
| 0x11E5  | ADD HL, DE | yes          | 11       |
| 0x11E6  | INC HL     | yes          | 6        |
| 0x11E7  | JR NC      | yes          | 12/7     |
| 0x11E9  | DEC (HL)   | yes          | 11       |
| 0x11EA  | JR Z       | yes          | 12/7     |
| 0x11EC  | DEC (HL)   | yes          | 11       |
| 0x11ED  | JR Z       | yes          | 12/7     |

FUSE 1356/1356 passes, confirming all ops are correct. No single buggy Op was found.

---

## Hard-Stop Invoked

Hard-stop condition: no buggy Op identified; the gate specification is incorrect.

**Follow-up required:** Redefine the FRAMES gate to 132 frames (or run 200 frames with
assertion that FRAMES ≥ 50). File as `zx80-1qc-next`.
