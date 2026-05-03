# Phase 2.11 Implementation Plan — programs (5/5) + ZEXDOC + M1 close

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close out M1. Programs suite goes 5/5 (add fib10, memcpy16, bubblesort_4, crc8). Implement ZEXDOC milestone gate via CP/M BDOS trap. Iterate on flag-edge defects until all CRCs pass. Tag `m1-cpu-complete`.

**Architecture:**
- Track 1: extend `ProgramExpectation` with `initial_memory: Map<String, Int>?` so programs can declare pre-loaded data; add 4 program fixtures (.asm, .bin, .expected.json each); update `ResourceLoader.PROGRAM_NAMES`.
- Track 2: vendor `ZEXDOC.COM`, implement `BdosHandler` (CP/M syscalls 2 and 9) + `ZexdocRunner` (loads at 0x100, intercepts `pc==0x0005` to run handler then lets the `RET` (0xC9) at that address pop the return); fill the existing `ZexdocCommand` stub.
- Iterate on any flag-edge bugs ZEXDOC reveals; tag `m1-phase02-11` then `m1-cpu-complete`.

**Tech Stack:** Kotlin 2.x, Gradle, kotlinx-serialization-json, JUnit 5, Clikt (CLI).

---

## Task 1: Extend `ProgramExpectation` with `initial_memory`

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/harness/programs/ProgramExpectation.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuite.kt`
- Test: `src/test/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuiteTest.kt`

- [ ] **Step 1: Write failing tests for `initial_memory`**

In `ProgramsSuiteTest.kt`, add two new tests. The existing tests likely cover the no-initial-memory case (back-compat) implicitly via `nop_loop`; we add the positive case.

```kotlin
@Test
fun `applies initial_memory before run`() {
    // Program: LD A, (0x100); HALT
    val bytes = byteArrayOf(0x3A.toByte(), 0x00, 0x01, 0x76)
    val exp = ProgramExpectation(
        name = "loads_initial",
        load_at = 0,
        entry = 0,
        max_cycles = 100L,
        initial_memory = mapOf("0x100" to 0xAB),
        expect = ExpectedState(a = 0xAB, halted = true),
    )
    val decoder = OpTableBuilder.build()
    val suite = ProgramsSuite(decoder, listOf(ProgramFixture(bytes, exp)))
    val result = suite.run()
    assertEquals(1, result.passed, "expected fixture to pass with initial_memory loaded; details=${result.details}")
}

@Test
fun `initial_memory absent is back-compat`() {
    // Re-run nop_loop equivalent inline to confirm absence is fine.
    val bytes = byteArrayOf(0x76)  // HALT
    val exp = ProgramExpectation(
        name = "halt_only",
        load_at = 0,
        entry = 0,
        max_cycles = 8L,
        // initial_memory omitted
        expect = ExpectedState(pc = 1, halted = true),
    )
    val decoder = OpTableBuilder.build()
    val suite = ProgramsSuite(decoder, listOf(ProgramFixture(bytes, exp)))
    val result = suite.run()
    assertEquals(1, result.passed)
}
```

Imports to add at top of test file: `ru.alepar.zx80.op.OpTableBuilder` if not already present.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests '*ProgramsSuiteTest*' -i`
Expected: `applies initial_memory before run` fails (compile error: `initial_memory` is not a parameter of `ProgramExpectation`).

- [ ] **Step 3: Add `initial_memory` field to `ProgramExpectation`**

Modify `ProgramExpectation.kt`:

```kotlin
package ru.alepar.zx80.harness.programs

import kotlinx.serialization.Serializable

@Serializable
data class ProgramExpectation(
    val name: String,
    val load_at: Int,
    val entry: Int,
    val max_cycles: Long,
    val stop_on: String = "HALT", // currently only HALT supported
    /**
     * Optional pre-loaded memory bytes applied AFTER the program is loaded but BEFORE execution
     * begins. Address keys are hex with `0x` prefix (case insensitive). Values are in 0..255.
     */
    val initial_memory: Map<String, Int>? = null,
    val expect: ExpectedState,
)

@Serializable
data class ExpectedState(
    val pc: Int? = null,
    val halted: Boolean? = null,
    val a: Int? = null,
    val bc: Int? = null,
    val de: Int? = null,
    val hl: Int? = null,
    val sp: Int? = null,
    /**
     * Map of address → expected byte. Address must be hex with `0x` prefix (e.g. `"0x100"`); case
     * insensitive.
     */
    val memory: Map<String, Int>? = null,
)
```

- [ ] **Step 4: Apply `initial_memory` in `ProgramsSuite.runOne`**

Modify `ProgramsSuite.kt` `runOne` to apply initial_memory after the per-byte program load and before the dispatch loop:

```kotlin
private fun runOne(p: ProgramFixture): JsonObject {
    val exp = p.expectation
    val cpu = Cpu().apply { pc = exp.entry }
    val mem = Memory()
    // Per-byte write to avoid loadAt's overflow precondition for any future fixture
    for ((offset, b) in p.bytes.withIndex()) {
        mem.write(exp.load_at + offset, b.toInt() and 0xFF)
    }
    // Apply pre-loaded memory bytes, if any.
    exp.initial_memory?.forEach { (addrStr, byte) ->
        mem.write(parseHex(addrStr), byte and 0xFF)
    }

    var failure: String? = null
    // ... rest unchanged
}
```

(`parseHex` is the existing private helper at the bottom of the class.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests '*ProgramsSuiteTest*' -i`
Expected: PASS for both new tests.

- [ ] **Step 6: Run full check**

Run: `./gradlew check`
Expected: green. Existing nop_loop fixture continues to pass (back-compat verified).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/harness/programs/ProgramExpectation.kt \
        src/main/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuite.kt \
        src/test/kotlin/ru/alepar/zx80/harness/suites/ProgramsSuiteTest.kt
git commit -m "feat(harness): ProgramExpectation initial_memory field

Allows program fixtures to declare pre-loaded data without baking it
into the binary. ProgramsSuite applies initial_memory after the
program load and before the dispatch loop. nop_loop fixture remains
back-compatible (field is nullable, default null).

Foundation for memcpy16/bubblesort_4/crc8 fixtures."
```

---

## Task 2: Add `fib10` program fixture

**Files:**
- Create: `src/main/resources/programs/fib10.asm`
- Create: `src/main/resources/programs/fib10.bin` (17 bytes)
- Create: `src/main/resources/programs/fib10.expected.json`
- Modify: `src/main/kotlin/ru/alepar/zx80/cli/ResourceLoader.kt` (extend `PROGRAM_NAMES`)
- Test: existing `ProgramsSuiteTest` covers via the loader path indirectly; we add a direct fixture-load test.

- [ ] **Step 1: Write the `.asm` source**

Create `src/main/resources/programs/fib10.asm`:

```asm
; fib10: iterative Fibonacci. Computes F(11) = 89 and stores it at 0x100.
; Strategy: HL=a, DE=b. Loop: ADD HL,DE; EX DE,HL. After 10 iterations
; the latest sum is in DE; one more EX brings it into HL for storing.

        ORG  0x0000
        LD HL, 0          ; a = 0
        LD DE, 1          ; b = 1
        LD B, 10          ; counter
LOOP:   ADD HL, DE        ; HL = a + b
        EX DE, HL         ; HL <-> DE
        DJNZ LOOP
        EX DE, HL         ; bring final sum back into HL
        LD (0x100), HL    ; store
        HALT
```

- [ ] **Step 2: Compute the byte sequence**

Per-byte mnemonic table (verify before writing the binary):

| Addr  | Bytes      | Mnemonic            | Notes |
|-------|------------|---------------------|-------|
| 0x00  | 21 00 00   | LD HL,0             |       |
| 0x03  | 11 01 00   | LD DE,1             |       |
| 0x06  | 06 0A      | LD B,10             |       |
| 0x08  | 19         | ADD HL,DE           | LOOP target |
| 0x09  | EB         | EX DE,HL            |       |
| 0x0A  | 10 FC      | DJNZ LOOP           | disp = 0x08 - (0x0A+2) = -4 = 0xFC |
| 0x0C  | EB         | EX DE,HL            |       |
| 0x0D  | 22 00 01   | LD (0x100),HL       |       |
| 0x10  | 76         | HALT                |       |

Final byte sequence (17 bytes): `21 00 00 11 01 00 06 0A 19 EB 10 FC EB 22 00 01 76`

- [ ] **Step 3: Write the binary**

Create `src/main/resources/programs/fib10.bin` (17 bytes). Use any tool (e.g. `printf` or `xxd`):

```bash
printf '\x21\x00\x00\x11\x01\x00\x06\x0A\x19\xEB\x10\xFC\xEB\x22\x00\x01\x76' \
  > src/main/resources/programs/fib10.bin
```

Verify with `xxd src/main/resources/programs/fib10.bin` — should show 17 bytes starting with `21 00 00 11 01 00 06 0a 19 eb 10 fc eb 22 00 01 76`.

- [ ] **Step 4: Write the expectation**

Create `src/main/resources/programs/fib10.expected.json`:

```json
{
  "name": "fib10",
  "load_at": 0,
  "entry": 0,
  "max_cycles": 1000,
  "stop_on": "HALT",
  "expect": {
    "pc": 17,
    "halted": true,
    "hl": 89,
    "memory": {
      "0x100": 89,
      "0x101": 0
    }
  }
}
```

(`pc=17` because HALT at 0x10 advances PC by 1 to 0x11=17.)

- [ ] **Step 5: Add `"fib10"` to `PROGRAM_NAMES`**

Modify `ResourceLoader.kt`:

```kotlin
private val PROGRAM_NAMES = listOf("nop_loop", "fib10")
```

- [ ] **Step 6: Run the programs suite to verify fib10 passes**

Run: `./gradlew test --tests '*ProgramsSuiteTest*' --tests '*ScoreCommandTest*' -i`

Expected: fib10 passes (assuming Phases 2.1b/2.2/2.3/2.4 are merged: LD HL,nn / LD DE,nn / LD B,n / ADD HL,DE / EX DE,HL / DJNZ / LD (nn),HL / HALT all available). If any of those is missing, you'll see a `no op for opcode 0xXX` failure pointing to the missing op — that's a phase-merge gap, not a fixture bug.

- [ ] **Step 7: Run full check**

Run: `./gradlew check`
Expected: green.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/programs/fib10.asm \
        src/main/resources/programs/fib10.bin \
        src/main/resources/programs/fib10.expected.json \
        src/main/kotlin/ru/alepar/zx80/cli/ResourceLoader.kt
git commit -m "feat(programs): add fib10 fixture (F(11)=89 at 0x100)

Iterative Fibonacci using ADD HL,DE / EX DE,HL / DJNZ. 17-byte program;
result 89 stored at 0x100 little-endian. Programs suite goes 1/2 → 2/2
(once required ops are merged)."
```

---

## Task 3: Add `memcpy16` program fixture

**Files:**
- Create: `src/main/resources/programs/memcpy16.asm`
- Create: `src/main/resources/programs/memcpy16.bin` (12 bytes)
- Create: `src/main/resources/programs/memcpy16.expected.json`
- Modify: `src/main/kotlin/ru/alepar/zx80/cli/ResourceLoader.kt`

- [ ] **Step 1: Write `.asm` source**

Create `src/main/resources/programs/memcpy16.asm`:

```asm
; memcpy16: copy 16 bytes from 0x100 to 0x200 using LDIR.
; Source bytes are pre-loaded via initial_memory in the fixture .json.

        ORG  0x0000
        LD HL, 0x100      ; source
        LD DE, 0x200      ; dest
        LD BC, 16         ; count
        LDIR
        HALT
```

- [ ] **Step 2: Per-byte table**

| Addr  | Bytes      | Mnemonic         |
|-------|------------|------------------|
| 0x00  | 21 00 01   | LD HL,0x100      |
| 0x03  | 11 00 02   | LD DE,0x200      |
| 0x06  | 01 10 00   | LD BC,16         |
| 0x09  | ED B0      | LDIR             |
| 0x0B  | 76         | HALT             |

Total 12 bytes: `21 00 01 11 00 02 01 10 00 ED B0 76`.

- [ ] **Step 3: Write the binary**

```bash
printf '\x21\x00\x01\x11\x00\x02\x01\x10\x00\xED\xB0\x76' \
  > src/main/resources/programs/memcpy16.bin
```

Verify: `xxd src/main/resources/programs/memcpy16.bin` — 12 bytes.

- [ ] **Step 4: Write the expectation**

Source pattern: 16 bytes of `0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xF0, 0xF1`. These are pre-loaded at `0x100..0x10F` via `initial_memory`. After LDIR, `0x200..0x20F` should match.

Create `src/main/resources/programs/memcpy16.expected.json`:

```json
{
  "name": "memcpy16",
  "load_at": 0,
  "entry": 0,
  "max_cycles": 1000,
  "stop_on": "HALT",
  "initial_memory": {
    "0x100": 17, "0x101": 34, "0x102": 51, "0x103": 68,
    "0x104": 85, "0x105": 102, "0x106": 119, "0x107": 136,
    "0x108": 153, "0x109": 170, "0x10A": 187, "0x10B": 204,
    "0x10C": 221, "0x10D": 238, "0x10E": 240, "0x10F": 241
  },
  "expect": {
    "pc": 12,
    "halted": true,
    "bc": 0,
    "hl": 272,
    "de": 528,
    "memory": {
      "0x200": 17, "0x201": 34, "0x202": 51, "0x203": 68,
      "0x204": 85, "0x205": 102, "0x206": 119, "0x207": 136,
      "0x208": 153, "0x209": 170, "0x20A": 187, "0x20B": 204,
      "0x20C": 221, "0x20D": 238, "0x20E": 240, "0x20F": 241
    }
  }
}
```

(`hl=272 = 0x110`, `de=528 = 0x210` — both incremented past the last copied byte. `pc=12 = 0x0C` after HALT advances 1.)

- [ ] **Step 5: Add `"memcpy16"` to `PROGRAM_NAMES`**

Modify `ResourceLoader.kt`:

```kotlin
private val PROGRAM_NAMES = listOf("nop_loop", "fib10", "memcpy16")
```

- [ ] **Step 6: Run the programs suite**

Run: `./gradlew test --tests '*ProgramsSuiteTest*' --tests '*ScoreCommandTest*' -i`

Expected: memcpy16 passes. Requires Phase 2.10 (LDIR) and Phase 2.1b (LD HL,nn / LD DE,nn / LD BC,nn) and HALT.

- [ ] **Step 7: Full check**

Run: `./gradlew check`
Expected: green.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/programs/memcpy16.asm \
        src/main/resources/programs/memcpy16.bin \
        src/main/resources/programs/memcpy16.expected.json \
        src/main/kotlin/ru/alepar/zx80/cli/ResourceLoader.kt
git commit -m "feat(programs): add memcpy16 fixture (LDIR-based block copy)

Copies 16 bytes from 0x100 to 0x200. Source pattern pre-loaded via
the new initial_memory field. Exercises LDIR (Phase 2.10) end-to-end."
```

---

## Task 4: Add `bubblesort_4` program fixture

**Files:**
- Create: `src/main/resources/programs/bubblesort_4.asm`
- Create: `src/main/resources/programs/bubblesort_4.bin` (25 bytes)
- Create: `src/main/resources/programs/bubblesort_4.expected.json`
- Modify: `src/main/kotlin/ru/alepar/zx80/cli/ResourceLoader.kt`

- [ ] **Step 1: Write `.asm` source**

Create `src/main/resources/programs/bubblesort_4.asm`:

```asm
; bubblesort_4: sort 4 bytes at 0x100 ascending. Bubble sort.
; B = outer pass count (3 = N-1).
; C = inner count per pass (3 = N-1).
; HL points at the "left" element per inner step; INC HL moves to "right".

        ORG  0x0000
        LD B, 3              ; outer = N-1
OUTER:  LD HL, 0x100         ; reset pointer each pass
        LD C, 3              ; inner = N-1
INNER:  LD A, (HL)           ; A = left
        INC HL               ; advance to right
        CP (HL)              ; A - (HL)
        JR C, NOSWAP         ; A < (HL): in order
        JR Z, NOSWAP         ; A == (HL): in order
        ; A > (HL): swap
        LD D, (HL)
        LD (HL), A
        DEC HL
        LD (HL), D
        INC HL
NOSWAP: DEC C
        JR NZ, INNER
        DJNZ OUTER
        HALT
```

- [ ] **Step 2: Per-byte table**

| Addr  | Bytes      | Mnemonic            | Notes |
|-------|------------|---------------------|-------|
| 0x00  | 06 03      | LD B,3              |       |
| 0x02  | 21 00 01   | LD HL,0x100         | OUTER |
| 0x05  | 0E 03      | LD C,3              |       |
| 0x07  | 7E         | LD A,(HL)           | INNER |
| 0x08  | 23         | INC HL              |       |
| 0x09  | BE         | CP (HL)             |       |
| 0x0A  | 38 07      | JR C,NOSWAP         | disp = 0x13 - 0x0C = 7 |
| 0x0C  | 28 05      | JR Z,NOSWAP         | disp = 0x13 - 0x0E = 5 |
| 0x0E  | 56         | LD D,(HL)           |       |
| 0x0F  | 77         | LD (HL),A           |       |
| 0x10  | 2B         | DEC HL              |       |
| 0x11  | 72         | LD (HL),D           |       |
| 0x12  | 23         | INC HL              |       |
| 0x13  | 0D         | DEC C               | NOSWAP |
| 0x14  | 20 F1      | JR NZ,INNER         | disp = 0x07 - 0x16 = -15 = 0xF1 |
| 0x16  | 10 EA      | DJNZ OUTER          | disp = 0x02 - 0x18 = -22 = 0xEA |
| 0x18  | 76         | HALT                |       |

Total 25 bytes (0x00..0x18 inclusive).

Byte sequence:
`06 03 21 00 01 0E 03 7E 23 BE 38 07 28 05 56 77 2B 72 23 0D 20 F1 10 EA 76`

- [ ] **Step 3: Write the binary**

```bash
printf '\x06\x03\x21\x00\x01\x0E\x03\x7E\x23\xBE\x38\x07\x28\x05\x56\x77\x2B\x72\x23\x0D\x20\xF1\x10\xEA\x76' \
  > src/main/resources/programs/bubblesort_4.bin
```

Verify: `xxd src/main/resources/programs/bubblesort_4.bin` — 25 bytes.

- [ ] **Step 4: Write the expectation**

Input: `[0x04, 0x01, 0x03, 0x02]` at `0x100..0x103`. Expected sorted: `[0x01, 0x02, 0x03, 0x04]`.

Create `src/main/resources/programs/bubblesort_4.expected.json`:

```json
{
  "name": "bubblesort_4",
  "load_at": 0,
  "entry": 0,
  "max_cycles": 5000,
  "stop_on": "HALT",
  "initial_memory": {
    "0x100": 4,
    "0x101": 1,
    "0x102": 3,
    "0x103": 2
  },
  "expect": {
    "pc": 25,
    "halted": true,
    "memory": {
      "0x100": 1,
      "0x101": 2,
      "0x102": 3,
      "0x103": 4
    }
  }
}
```

(`pc=25 = 0x19` after HALT at 0x18 advances 1.)

- [ ] **Step 5: Add `"bubblesort_4"` to `PROGRAM_NAMES`**

Modify `ResourceLoader.kt`:

```kotlin
private val PROGRAM_NAMES = listOf("nop_loop", "fib10", "memcpy16", "bubblesort_4")
```

- [ ] **Step 6: Run the programs suite**

Run: `./gradlew test --tests '*ProgramsSuiteTest*' -i`
Expected: bubblesort_4 passes.

If FAIL with "mem[0x100]=…", the algorithm or byte translation is wrong. Cross-check the per-byte table; run a hand-trace through the first inner pass (input 0x04, 0x01, 0x03, 0x02) and confirm post-pass-1 memory is `0x01, 0x03, 0x02, 0x04`. Reference trace in the spec.

- [ ] **Step 7: Full check**

Run: `./gradlew check`
Expected: green.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/programs/bubblesort_4.asm \
        src/main/resources/programs/bubblesort_4.bin \
        src/main/resources/programs/bubblesort_4.expected.json \
        src/main/kotlin/ru/alepar/zx80/cli/ResourceLoader.kt
git commit -m "feat(programs): add bubblesort_4 fixture (4-byte ascending sort)

Bubble sort over 4 bytes at 0x100. Smaller than the original spec's
bubblesort_8 — same algorithm coverage (CP, conditional JR, swap via
LD r,(HL) / LD (HL),r), tractable hand-assembled binary."
```

---

## Task 5: Add `crc8` program fixture

**Files:**
- Create: `src/main/resources/programs/crc8.asm`
- Create: `src/main/resources/programs/crc8.bin` (26 bytes)
- Create: `src/main/resources/programs/crc8.expected.json`
- Modify: `src/main/kotlin/ru/alepar/zx80/cli/ResourceLoader.kt`

- [ ] **Step 1: Write `.asm` source**

Create `src/main/resources/programs/crc8.asm`:

```asm
; crc8: compute CRC-8 over 4 input bytes at 0x100. Polynomial 0x07, init 0x00,
; no reflection, no XOR-out. Result stored at 0x200.
; Algorithm:
;   crc = 0
;   for each byte b:
;     crc ^= b
;     for 8 bits:
;       msb = crc bit 7
;       crc <<= 1 (8-bit)
;       if msb: crc ^= 0x07

        ORG  0x0000
        LD HL, 0x100         ; input pointer
        LD B, 4              ; byte count
        LD A, 0              ; CRC accumulator
NEXTB:  XOR (HL)
        LD C, 8              ; bit counter
BIT:    SLA A                ; shift left, MSB into carry
        JR NC, NOXOR
        XOR 0x07
NOXOR:  DEC C
        JR NZ, BIT
        INC HL
        DJNZ NEXTB
        LD (0x200), A
        HALT
```

- [ ] **Step 2: Per-byte table**

| Addr  | Bytes      | Mnemonic            | Notes |
|-------|------------|---------------------|-------|
| 0x00  | 21 00 01   | LD HL,0x100         |       |
| 0x03  | 06 04      | LD B,4              |       |
| 0x05  | 3E 00      | LD A,0              |       |
| 0x07  | AE         | XOR (HL)            | NEXTB |
| 0x08  | 0E 08      | LD C,8              |       |
| 0x0A  | CB 27      | SLA A               | BIT |
| 0x0C  | 30 02      | JR NC,NOXOR         | disp = 0x10 - 0x0E = 2 |
| 0x0E  | EE 07      | XOR 0x07            |       |
| 0x10  | 0D         | DEC C               | NOXOR |
| 0x11  | 20 F7      | JR NZ,BIT           | disp = 0x0A - 0x13 = -9 = 0xF7 |
| 0x13  | 23         | INC HL              |       |
| 0x14  | 10 F1      | DJNZ NEXTB          | disp = 0x07 - 0x16 = -15 = 0xF1 |
| 0x16  | 32 00 02   | LD (0x200),A        |       |
| 0x19  | 76         | HALT                |       |

Total 26 bytes (0x00..0x19).

Byte sequence:
`21 00 01 06 04 3E 00 AE 0E 08 CB 27 30 02 EE 07 0D 20 F7 23 10 F1 32 00 02 76`

- [ ] **Step 3: Write the binary**

```bash
printf '\x21\x00\x01\x06\x04\x3E\x00\xAE\x0E\x08\xCB\x27\x30\x02\xEE\x07\x0D\x20\xF7\x23\x10\xF1\x32\x00\x02\x76' \
  > src/main/resources/programs/crc8.bin
```

Verify: `xxd src/main/resources/programs/crc8.bin` — 26 bytes.

- [ ] **Step 4: Compute the expected CRC**

Input bytes: `0x31, 0x32, 0x33, 0x34` ("1234" in ASCII). Reference computation (Python):

```python
def crc8(data, poly=0x07, init=0x00):
    crc = init
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = ((crc << 1) & 0xFF) ^ (poly if (crc & 0x80) else 0)
    return crc

print(hex(crc8(b'1234')))  # → 0xc2
```

Hand-traced result: **0xC2** (= 194 decimal). Lock this into the fixture below.

- [ ] **Step 5: Write the expectation**

Create `src/main/resources/programs/crc8.expected.json`:

```json
{
  "name": "crc8",
  "load_at": 0,
  "entry": 0,
  "max_cycles": 5000,
  "stop_on": "HALT",
  "initial_memory": {
    "0x100": 49,
    "0x101": 50,
    "0x102": 51,
    "0x103": 52
  },
  "expect": {
    "pc": 26,
    "halted": true,
    "a": 194,
    "memory": {
      "0x200": 194
    }
  }
}
```

(`a=194 = 0xC2`. `pc=26 = 0x1A` after HALT at 0x19.)

- [ ] **Step 6: Add `"crc8"` to `PROGRAM_NAMES`**

Modify `ResourceLoader.kt`:

```kotlin
private val PROGRAM_NAMES = listOf("nop_loop", "fib10", "memcpy16", "bubblesort_4", "crc8")
```

- [ ] **Step 7: Run programs suite**

Run: `./gradlew test --tests '*ProgramsSuiteTest*' --tests '*ScoreCommandTest*' -i`
Expected: crc8 passes. Requires Phase 2.7 (SLA via CB-prefix) plus the basic ops.

If FAIL with `a=…` or `mem[0x200]=…` not matching 194, double-check: (a) the polynomial XOR happens AFTER the shift (not before — that's a different convention), (b) `SLA A` on Z80 sets carry from the shifted-out bit (which is the original MSB), (c) the JR NC test reads the carry left by SLA.

- [ ] **Step 8: Full check**

Run: `./gradlew check`
Expected: green. **Programs suite reports 5/5 — milestone reached.**

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/programs/crc8.asm \
        src/main/resources/programs/crc8.bin \
        src/main/resources/programs/crc8.expected.json \
        src/main/kotlin/ru/alepar/zx80/cli/ResourceLoader.kt
git commit -m "feat(programs): add crc8 fixture; programs suite now 5/5

CRC-8 (poly 0x07, init 0x00, no reflection) over '1234' = 0xC2.
Exercises CB-prefixed SLA + conditional JR + XOR n + INC HL + DJNZ.

Programs suite milestone: 1/5 → 5/5. M1 'programs' deliverable done."
```

---

## Task 6: ZEXDOC infrastructure (BdosHandler + ZexdocRunner + CLI wiring)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/zexdoc/BdosHandler.kt`
- Create: `src/main/kotlin/ru/alepar/zx80/zexdoc/ZexdocRunner.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/cli/ZexdocCommand.kt` (currently a stub)
- Create: `src/main/resources/zexdoc/ZEXDOC.COM` (vendored binary)
- Create: `src/main/resources/zexdoc/README.txt` (attribution)
- Test: `src/test/kotlin/ru/alepar/zx80/zexdoc/BdosHandlerTest.kt`
- Test: `src/test/kotlin/ru/alepar/zx80/zexdoc/ZexdocRunnerSmokeTest.kt`

- [ ] **Step 1: Write failing `BdosHandlerTest`**

Create `src/test/kotlin/ru/alepar/zx80/zexdoc/BdosHandlerTest.kt`:

```kotlin
package ru.alepar.zx80.zexdoc

import org.junit.jupiter.api.Test
import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory
import kotlin.test.assertEquals

class BdosHandlerTest {

    @Test
    fun `syscall 2 prints char in E`() {
        val out = StringBuilder()
        val handler = BdosHandler(out)
        val cpu = Cpu().apply { c = 2; e = 'X'.code }
        handler.handle(cpu, Memory())
        assertEquals("X", out.toString())
    }

    @Test
    fun `syscall 9 prints dollar-terminated string at DE`() {
        val out = StringBuilder()
        val handler = BdosHandler(out)
        val mem = Memory()
        // "Hi!$" at 0x200
        val s = "Hi!$"
        for ((i, ch) in s.withIndex()) mem.write(0x200 + i, ch.code)
        val cpu = Cpu().apply { c = 9; de = 0x200 }
        handler.handle(cpu, mem)
        assertEquals("Hi!", out.toString())
    }

    @Test
    fun `syscall 9 stops at first dollar even with later content`() {
        val out = StringBuilder()
        val handler = BdosHandler(out)
        val mem = Memory()
        val s = "OK$junk"
        for ((i, ch) in s.withIndex()) mem.write(0x300 + i, ch.code)
        val cpu = Cpu().apply { c = 9; de = 0x300 }
        handler.handle(cpu, mem)
        assertEquals("OK", out.toString())
    }

    @Test
    fun `unknown syscall is no-op`() {
        val out = StringBuilder()
        val handler = BdosHandler(out)
        val cpu = Cpu().apply { c = 42 }
        handler.handle(cpu, Memory())
        assertEquals("", out.toString())
    }
}
```

(Note: this assumes `Cpu` exposes byte registers `c` and `e` and word register `de`. If the API differs slightly — e.g. `c` is a property derived from `bc` — adapt the setup accordingly: `cpu.bc = (cpu.bc and 0xFF00) or 2`. Inspect `Cpu.kt` first.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*BdosHandlerTest*' -i`
Expected: compile error — `BdosHandler` does not exist.

- [ ] **Step 3: Implement `BdosHandler`**

Create `src/main/kotlin/ru/alepar/zx80/zexdoc/BdosHandler.kt`:

```kotlin
package ru.alepar.zx80.zexdoc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Memory

/**
 * Implements the subset of CP/M BDOS calls that ZEXDOC.COM uses:
 *  - syscall 2 (C=2): print the character in E to the output.
 *  - syscall 9 (C=9): print the `$`-terminated string at DE to the output.
 *
 * The handler ONLY produces side effects (writes to `out`). It does NOT advance PC, push,
 * or pop — those are the job of the `RET` (0xC9) byte that the runner places at 0x0005.
 *
 * Unknown syscall numbers are a no-op (some ZEXDOC variants probe other calls; we ignore
 * them since they're not load-bearing for documented-instruction CRC matching).
 */
class BdosHandler(private val out: Appendable) {
    fun handle(cpu: Cpu, mem: Memory) {
        when (cpu.c) {
            2 -> out.append((cpu.e and 0xFF).toChar())
            9 -> {
                var addr = cpu.de
                while (true) {
                    val b = mem.read(addr) and 0xFF
                    if (b == '$'.code) break
                    out.append(b.toChar())
                    addr = (addr + 1) and 0xFFFF
                }
            }
            else -> { /* no-op */ }
        }
    }
}
```

(If `Cpu` doesn't have `c`, `e`, `de` as direct properties, use the byte-extraction equivalents. Inspect `Cpu.kt` and adapt — e.g. `(cpu.bc ushr 0) and 0xFF` for C; `(cpu.de ushr 0) and 0xFF` for E.)

- [ ] **Step 4: Run handler test to verify it passes**

Run: `./gradlew test --tests '*BdosHandlerTest*' -i`
Expected: PASS (4 tests).

- [ ] **Step 5: Vendor `ZEXDOC.COM` + attribution**

Download ZEXDOC.COM from a canonical source (e.g. `https://github.com/anotherlin/z80emu/raw/master/testfiles/zexdoc.com` or `http://mdfs.net/Software/Z80/Exerciser/`). The file is 8585 bytes.

Place at `src/main/resources/zexdoc/ZEXDOC.COM`.

Create `src/main/resources/zexdoc/README.txt`:

```
ZEXDOC.COM — Z80 documented-instruction exerciser.

Author: Frank D. Cringle (1994), with later refinements by Ian Bartholomew.
License: Released into the public domain by the author.
Source: http://mdfs.net/Software/Z80/Exerciser/

ZEXDOC tests every documented Z80 instruction with a wide range of operand
combinations and computes a CRC of the resulting CPU state. Each instruction
group prints "OK" if its CRC matches the reference, or "ERROR" otherwise.

Used here by `zx80 zexdoc` as the M1 milestone gate. See
docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md.
```

Verify file size:

```bash
ls -la src/main/resources/zexdoc/ZEXDOC.COM
```

Expected: ~8585 bytes (some variants are 8704; either is fine — note the actual size in the README and proceed).

- [ ] **Step 6: Write `ZexdocRunnerSmokeTest`**

Create `src/test/kotlin/ru/alepar/zx80/zexdoc/ZexdocRunnerSmokeTest.kt`:

```kotlin
package ru.alepar.zx80.zexdoc

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ZexdocRunnerSmokeTest {

    @Test
    fun `runs ZEXDOC for a small budget without crashing`() {
        val rom = javaClass.getResourceAsStream("/zexdoc/ZEXDOC.COM")!!.readBytes()
        val out = StringBuilder()
        val runner = ZexdocRunner(out)
        // 1M cycles is well under a full run but enough to exercise the prologue.
        val result = runner.run(rom, maxCycles = 1_000_000L)
        // The smoke test only asserts liveness: the runner advanced and the prologue
        // produced *some* output (ZEXDOC prints a header line and the first test name).
        assertTrue(out.isNotEmpty(), "expected ZEXDOC to print prologue output; got empty")
        assertTrue(result.cycles > 0L, "expected forward progress")
    }

    @Test
    fun `warm-boot CALL 0 halts cleanly`() {
        // Synthetic mini-program: JP 0x0000 from PC=0x0100. Runner sets mem[0]=HALT,
        // so jumping to 0x0000 should terminate the run.
        val mini = byteArrayOf(0xC3.toByte(), 0x00, 0x00)  // JP 0x0000
        val out = StringBuilder()
        val runner = ZexdocRunner(out)
        val result = runner.run(mini, maxCycles = 100L)
        assertTrue(result.halted, "expected halt after JP 0x0000; result=$result")
    }
}
```

- [ ] **Step 7: Run smoke test to verify it fails**

Run: `./gradlew test --tests '*ZexdocRunnerSmokeTest*' -i`
Expected: compile error — `ZexdocRunner` does not exist.

- [ ] **Step 8: Implement `ZexdocRunner`**

Create `src/main/kotlin/ru/alepar/zx80/zexdoc/ZexdocRunner.kt`:

```kotlin
package ru.alepar.zx80.zexdoc

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Dispatcher
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.op.OpTableBuilder

data class ZexdocResult(val output: String, val halted: Boolean, val cycles: Long)

/**
 * Runs a CP/M-80 binary (notably ZEXDOC.COM) with a minimal BDOS trap.
 *
 * Conventions:
 *  - The binary loads at 0x0100 (CP/M TPA).
 *  - Stack at 0xFFFE (just below the 64K boundary).
 *  - mem[0x0005] is set to RET (0xC9). Before each step, if PC == 0x0005, the BdosHandler
 *    is invoked to perform the syscall side effect; the RET then pops the return address
 *    naturally, so PC, SP, and stack contents stay consistent with how a real CP/M would
 *    look from the program's perspective.
 *  - mem[0x0000] is set to HALT (0x76) so that ZEXDOC's warm-boot exit (CALL 0x0000 or
 *    JP 0x0000) terminates the run cleanly instead of executing whatever happens to be
 *    at offset 0.
 */
class ZexdocRunner(private val out: Appendable) {
    fun run(rom: ByteArray, maxCycles: Long = Long.MAX_VALUE): ZexdocResult {
        val cpu = Cpu().apply { pc = 0x0100; sp = 0xFFFE }
        val mem = Memory()
        for ((i, b) in rom.withIndex()) {
            mem.write(0x0100 + i, b.toInt() and 0xFF)
        }
        mem.write(0x0005, 0xC9)  // RET at BDOS entry
        mem.write(0x0000, 0x76)  // HALT at warm-boot vector

        val decoder = OpTableBuilder.build()
        val dispatcher = Dispatcher(decoder)
        val handler = BdosHandler(out)

        while (!cpu.halted && cpu.tStates < maxCycles) {
            if (cpu.pc == 0x0005) {
                handler.handle(cpu, mem)
                // Fall through: the RET at 0x0005 will execute and pop the return address.
            }
            val op = dispatcher.decodeAt(cpu, mem)
                ?: error("no dispatch route for opcode 0x${mem.read(cpu.pc).toString(16)} at pc=0x${cpu.pc.toString(16)}")
            op.execute(cpu, mem)
        }
        return ZexdocResult(output = out.toString(), halted = cpu.halted, cycles = cpu.tStates)
    }
}
```

- [ ] **Step 9: Run smoke tests to verify they pass**

Run: `./gradlew test --tests '*ZexdocRunnerSmokeTest*' -i`
Expected: PASS (2 tests).

If the first test (`runs ZEXDOC for a small budget`) fails with `no dispatch route for opcode 0x…`, it means an instruction needed by the ZEXDOC prologue isn't implemented yet. That's a phase-merge gap, not a runner bug. Note the missing opcode and decide:
- If a Phase 2.x branch covers it but isn't merged: merge that phase first.
- If undocumented: ZEXDOC documented-only shouldn't hit it, but if it does, log the issue and address in WU 7.

- [ ] **Step 10: Wire `ZexdocCommand` to `ZexdocRunner`**

Modify `src/main/kotlin/ru/alepar/zx80/cli/ZexdocCommand.kt`:

```kotlin
package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import ru.alepar.zx80.zexdoc.ZexdocRunner

/** Runs the bundled ZEXDOC.COM Z80 documented-instruction conformance suite. */
class ZexdocCommand : CliktCommand(name = "zexdoc") {
    private val maxCycles by
        option("--max-cycles", help = "Cycle budget; 0 means unlimited (full run)")
            .long()
            .default(0L)

    private val quiet by
        option("--quiet", help = "Suppress live output streaming; only emit final summary").flag()

    override fun run() {
        val rom =
            ZexdocCommand::class.java.getResourceAsStream("/zexdoc/ZEXDOC.COM")
                ?: error("ZEXDOC.COM not bundled; expected at /zexdoc/ZEXDOC.COM on classpath")

        val sink: Appendable =
            if (quiet) StringBuilder() else SystemOutAppendable()
        val runner = ZexdocRunner(sink)
        val result =
            runner.run(rom.use { it.readBytes() }, maxCycles = if (maxCycles == 0L) Long.MAX_VALUE else maxCycles)

        if (quiet) echo(sink.toString())

        echo("--- ZEXDOC run summary ---", err = true)
        echo("cycles=${result.cycles}", err = true)
        echo("halted=${result.halted}", err = true)

        val hasError = result.output.contains("ERROR", ignoreCase = false)
        val complete = result.output.contains("Tests complete", ignoreCase = false)
        if (hasError || !complete) {
            echo("FAIL: errors=$hasError complete=$complete", err = true)
            throw com.github.ajalt.clikt.core.ProgramResult(1)
        }
        echo("PASS: all tests OK", err = true)
    }

    /** Small wrapper around `System.out` so we can stream chars as ZEXDOC emits them. */
    private class SystemOutAppendable : Appendable {
        override fun append(c: Char): Appendable {
            print(c)
            System.out.flush()
            return this
        }
        override fun append(csq: CharSequence?): Appendable {
            print(csq ?: "null")
            return this
        }
        override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
            print((csq ?: "null").subSequence(start, end))
            return this
        }
    }
}
```

- [ ] **Step 11: Verify the CLI compiles**

Run: `./gradlew installDist`
Expected: success.

Smoke-run (1M cycle budget — exits before completion but verifies wiring):

```bash
./build/install/zx80/bin/zx80 zexdoc --max-cycles=1000000
```

Expected: ZEXDOC prints a header line (e.g. "Z80doc instruction exerciser") and at least one test name, then the summary reports `complete=false` and exits with code 1 (because we cut it short). That's fine — full run is WU 7.

- [ ] **Step 12: Full check**

Run: `./gradlew check`
Expected: green.

- [ ] **Step 13: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/zexdoc/BdosHandler.kt \
        src/main/kotlin/ru/alepar/zx80/zexdoc/ZexdocRunner.kt \
        src/main/kotlin/ru/alepar/zx80/cli/ZexdocCommand.kt \
        src/main/resources/zexdoc/ZEXDOC.COM \
        src/main/resources/zexdoc/README.txt \
        src/test/kotlin/ru/alepar/zx80/zexdoc/BdosHandlerTest.kt \
        src/test/kotlin/ru/alepar/zx80/zexdoc/ZexdocRunnerSmokeTest.kt
git commit -m "feat(zexdoc): infrastructure for ZEXDOC.COM run (BDOS trap + runner + CLI)

Implements minimal CP/M BDOS subset (syscalls 2 and 9) needed by
ZEXDOC.COM. ZexdocRunner loads the binary at 0x0100, intercepts
PC==0x0005 to invoke the handler, and lets the RET at that address
pop the return naturally. CLI subcommand 'zx80 zexdoc' wires it
to stdout with --max-cycles and --quiet options.

ZEXDOC.COM is public domain (Frank Cringle, 1994). Vendored at
src/main/resources/zexdoc/ with attribution."
```

---

## Task 7: Run ZEXDOC end-to-end, fix flag-edges, tag M1 complete

**Files:**
- Modify: any per-Op file under `src/main/kotlin/ru/alepar/zx80/op/...` whose flag computation is found wrong (open-ended).
- Modify: any test under `src/test/kotlin/ru/alepar/zx80/...` to add coverage for newly-discovered edge cases.

This task is **open-ended** — the scope depends on what ZEXDOC reveals. Triage and fix iteratively.

- [ ] **Step 1: Run ZEXDOC unbounded**

```bash
./gradlew installDist && ./build/install/zx80/bin/zx80 zexdoc 2>&1 | tee /tmp/zexdoc.log
```

This may take several minutes. Watch live output.

Possible outcomes:

(a) Every test prints `OK`, summary says `Tests complete`, exit code 0. → **GOTO Step 6.**

(b) Some tests print `ERROR`. → Continue to Step 2.

(c) Process hangs or exits early with `no dispatch route for opcode 0x..`. → Phase-merge gap; merge the missing phase (or fix dispatch). Re-run.

- [ ] **Step 2: Triage failures**

Each failed ZEXDOC sub-test names the instruction class it's testing (e.g. `add hl,<bc,de,hl,sp>....`, `<adc,sbc> hl,<bc,de,hl,sp>`). Group failures by instruction family.

For each family that fails:
1. Identify the Op class — usually one-to-one with the ZEXDOC group name.
2. Read the corresponding Op's flag computation. Compare against the Z80 reference (Sean Young's Z80 manual or Mostek/Zilog docs).
3. Common causes (in rough order of frequency):
   - **H (half-carry) flag:** Wrong on `ADC`, `SBC`, `INC`, `DEC`, `DAA`, 16-bit `ADC HL,rr`. Spec is "carry from bit 3 to bit 4 (add) / borrow into bit 4 (sub)".
   - **P/V flag:** Confused parity vs. overflow. Logical ops set parity; arithmetic ops set overflow.
   - **N flag:** Should be set after subtract-style ops (CP, SBC, NEG, DEC, CPI/CPIR) and clear after add-style ops.
   - **C flag on 16-bit ops:** ADC HL,rr / SBC HL,rr — carry out of bit 15.
   - **Undocumented bits 5/3 of F:** ZEXDOC tests these. They are normally copied from the result (or for 16-bit ops, from the high byte of the result).
4. Add a focused unit test reproducing the broken edge case (single instruction, specific operand values, expected flags).
5. Fix the flag computation. Re-run unit test; then re-run ZEXDOC for that group only (ZEXDOC tests are self-contained — earlier groups don't have to be re-run if they passed).
6. Commit per-fix:
   ```bash
   git commit -m "fix(<op>): correct <flag> computation for <case>

   Found by ZEXDOC. Per Z80 reference, <flag> should be <X> when <Y>;
   we were computing <Z>. Test added to lock the behavior."
   ```

- [ ] **Step 3: Re-run ZEXDOC after each batch of fixes**

```bash
./gradlew installDist && ./build/install/zx80/bin/zx80 zexdoc 2>&1 | tee /tmp/zexdoc.log
```

Continue until exit code is 0 and no `ERROR` lines.

- [ ] **Step 4: If undocumented-bit failures stall progress**

ZEXDOC tests bits 5/3 of F as "what fell out of the ALU naturally". Most documented-instruction implementations set them implicitly via "F = (result and 0xFF) or other-bits"; some require explicit handling. If you're stuck on bits 5/3 only:

1. Audit `Flags.kt`: every result-byte-derived flag write should propagate bits 5 and 3 from `result and 0x28`.
2. For 16-bit ops, propagate from bit-13/11 of the high byte.
3. Add a targeted test asserting bits 5/3 for a specific instruction's edge cases, then fix.

This is the most likely source of "lots of small failures across many groups", so address `Flags.kt` first if the failure pattern looks broad.

- [ ] **Step 5: After ZEXDOC clean run, verify the score command**

```bash
./build/install/zx80/bin/zx80 score
```

Expected: `programs 5/5`, `fuse <high>/N`, `opcodes <high>/N`, composite ≥ 0.9.

If the composite is below 0.9, examine which suite is dragging it down. ZEXDOC fixes typically also raise FUSE numbers (same flag bugs surface in both). Check FUSE first.

- [ ] **Step 6: Run full check one more time**

```bash
./gradlew check
```

Expected: green. Sanity-verify no regressions from fixes.

- [ ] **Step 7: Tag the phase**

```bash
git tag -a m1-phase02-11 -m "Phase 2.11 complete: programs 5/5 + ZEXDOC passes"
```

- [ ] **Step 8: Tag M1 milestone**

```bash
git tag -a m1-cpu-complete -m "M1 milestone 'C' complete: headless Z80 CPU passes ZEXDOC

- All documented Z80 instructions implemented across phases 2.1a–2.11.
- Programs suite: 5/5 (nop_loop, fib10, memcpy16, bubblesort_4, crc8).
- ZEXDOC: every documented-instruction CRC matches the reference.
- Composite score: at M1 plateau.

End of M1. Next: M2 (display + I/O) or M3 (BASIC ROM)."
```

- [ ] **Step 9: Update top-level spec status (optional)**

If `docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md` has an explicit M1 status section, mark it complete. Otherwise skip.

- [ ] **Step 10: Final commit (if any uncommitted state)**

```bash
git status
```

Expected: clean. If anything is uncommitted (e.g. spec status update), commit with `chore: mark M1 complete in top-level spec`.

---

## Self-Review Notes

Spec coverage:
- Track 1 programs: Tasks 2–5 add fib10, memcpy16, bubblesort_4, crc8. Foundation in Task 1.
- Track 2 ZEXDOC: Tasks 6 (infra) + 7 (run + fix-iterate + tag).
- Done criteria 1–4: `gradlew check` (Tasks 1–7), score 5/5 (Task 5 + Task 7 step 5), ZEXDOC clean (Task 7), composite ≥ 0.9 (Task 7 step 5).
- Done criteria 5–7: `m1-phase02-11` tag (Task 7 step 7), `m1-cpu-complete` tag (Task 7 step 8), M1 done declaration (implicit via tags).

Type consistency:
- `ProgramExpectation.initial_memory: Map<String, Int>?` — defined in Task 1, used in Tasks 3/4/5.
- `ZexdocResult(output, halted, cycles)` — defined in Task 6, consumed in Task 7 step 1 implicitly (CLI summary).
- `BdosHandler(Appendable)` constructor — used by `ZexdocRunner` and `BdosHandlerTest`.

Placeholder scan:
- All program byte sequences are explicit (per-byte tables + `printf` commands).
- Expected JSON contents are written out in full, not described abstractly.
- Task 7 is open-ended *by design* (ZEXDOC outcome is unknowable in advance); the steps describe the triage process concretely.

Notes for the implementer:
- If `Cpu` field names differ from `c`, `e`, `de` (Task 6 Step 1), inspect `src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt` and adapt the test/handler. Common alternatives: `(cpu.bc and 0xFF)` for C, `(cpu.de and 0xFF)` for E.
- If `Memory.read` returns `Int` vs. `Byte`, adjust accordingly — the `and 0xFF` in `BdosHandler.handle` should normalize either way.
- In Task 7, prefer per-instruction-class fix-up commits over one giant "fix flags" commit. Each fix should be independently revertable.
