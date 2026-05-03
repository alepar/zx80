# Phase G — ED Alternate Slots + X/Y Leaks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the 21 residual FUSE failures by installing 18 Z80-standard ED alternate slots (NEG/RETN/RETI/IM aliases) and adding result-derived X/Y bits to four ED ops with inline flag computation that Phase D missed.

**Architecture:** Two independent fixes. G-1 extends `EdOps.installInto` with a new `installAlternateOpcodes` call BEFORE `installEdNopFallback` — installs `Neg`, `Retn`, `Reti`, and `Im(n)` instances at the 18 alias slots. G-2 adds one-line X/Y OR to four ED ops (LdAI, LdAR, Rrd, Rld) at the end of their inline F computation. G-3 sweeps and tags `m1-phase-g`.

**Tech Stack:** Kotlin (JDK 21), JUnit 5, AssertJ, Gradle, Spotless ktlint.

**Spec:** `docs/superpowers/specs/2026-05-03-zx80-phase-g-ed-aliases-and-xy-leaks-design.md`

---

## Universal patterns

**TDD per task:** failing test → run → confirm FAIL → modify → re-run → confirm PASS.

**Test name rules:** avoid `..`, `->`, `;` in backtick names.

**Beads workflow per WU:** claim → execute → `./gradlew test spotlessApply` → commit per plan → `bd close <id>`.

## File Structure

**Modified files:**
- `src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt` — new `installAlternateOpcodes` method (WU G-1)
- `src/test/kotlin/ru/alepar/zx80/op/ed/EdOpsTest.kt` — alias-slot assertions (WU G-1)
- `src/main/kotlin/ru/alepar/zx80/op/ed/LdAI.kt` — append X/Y to F (WU G-2)
- `src/main/kotlin/ru/alepar/zx80/op/ed/LdAR.kt` — append X/Y to F (WU G-2)
- `src/main/kotlin/ru/alepar/zx80/op/ed/Rrd.kt` — append X/Y to F (WU G-2)
- `src/main/kotlin/ru/alepar/zx80/op/ed/Rld.kt` — append X/Y to F (WU G-2)
- `src/test/kotlin/ru/alepar/zx80/op/ed/LdAITest.kt` — X/Y test (WU G-2)
- `src/test/kotlin/ru/alepar/zx80/op/ed/LdARTest.kt` — X/Y test (WU G-2)
- `src/test/kotlin/ru/alepar/zx80/op/ed/RrdTest.kt` — X/Y test (WU G-2)
- `src/test/kotlin/ru/alepar/zx80/op/ed/RldTest.kt` — X/Y test (WU G-2)

No new files.

---

## WU G-1 — ED alternate slot installation (18 slots)

Beads: `<BD-ID-WU-G-1>`. Install the 18 alias slots before the EdNop fallback.

### Task 1: Append failing tests to EdOpsTest

**Files:**
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/EdOpsTest.kt`

- [ ] **Step 1: Append failing tests**

```kotlin
@Test
fun `installInto registers NEG at 7 alternate slots besides ED 0x44`() {
    val d = Decoder()
    EdOps.installInto(d)
    val canonical = d.ed[0x44]
    assertThat(canonical).isSameAs(Neg)
    for (alt in listOf(0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C)) {
        assertThat(d.ed[alt]).`as`("ED 0x%02X NEG alias", alt).isSameAs(Neg)
    }
}

@Test
fun `installInto registers RETN at 3 alternate slots besides ED 0x45`() {
    val d = Decoder()
    EdOps.installInto(d)
    assertThat(d.ed[0x45]).isSameAs(Retn)
    for (alt in listOf(0x55, 0x65, 0x75)) {
        assertThat(d.ed[alt]).`as`("ED 0x%02X RETN alias", alt).isSameAs(Retn)
    }
}

@Test
fun `installInto registers RETI at 3 alternate slots besides ED 0x4D`() {
    val d = Decoder()
    EdOps.installInto(d)
    assertThat(d.ed[0x4D]).isSameAs(Reti)
    for (alt in listOf(0x5D, 0x6D, 0x7D)) {
        assertThat(d.ed[alt]).`as`("ED 0x%02X RETI alias", alt).isSameAs(Reti)
    }
}

@Test
fun `installInto registers IM 0 alias at ED 0x4E, 0x66, 0x6E`() {
    val d = Decoder()
    EdOps.installInto(d)
    for (alt in listOf(0x4E, 0x66, 0x6E)) {
        val op = d.ed[alt]
        assertThat(op).`as`("ED 0x%02X is IM 0", alt).isInstanceOf(Im::class.java)
        assertThat((op as Im).mode).`as`("ED 0x%02X mode", alt).isEqualTo(0)
    }
}

@Test
fun `installInto registers IM 1 alias at ED 0x76`() {
    val d = Decoder()
    EdOps.installInto(d)
    val op = d.ed[0x76]
    assertThat(op).isInstanceOf(Im::class.java)
    assertThat((op as Im).mode).isEqualTo(1)
}

@Test
fun `installInto registers IM 2 alias at ED 0x7E`() {
    val d = Decoder()
    EdOps.installInto(d)
    val op = d.ed[0x7E]
    assertThat(op).isInstanceOf(Im::class.java)
    assertThat((op as Im).mode).isEqualTo(2)
}

@Test
fun `installInto leaves alternate slots NOT as EdNop`() {
    val d = Decoder()
    EdOps.installInto(d)
    val aliasSlots =
        listOf(
            0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C, // NEG
            0x55, 0x65, 0x75, // RETN
            0x5D, 0x6D, 0x7D, // RETI
            0x4E, 0x66, 0x6E, 0x76, 0x7E, // IM
        )
    for (slot in aliasSlots) {
        assertThat(d.ed[slot]).`as`("ED 0x%02X must not be EdNop", slot).isNotSameAs(EdNop)
    }
}
```

Add the import for `Im` if needed:
```kotlin
import ru.alepar.zx80.op.misc.Im
```

- [ ] **Step 2: Run to verify failures**

Run: `./gradlew test --tests EdOpsTest`
Expected: FAIL — currently those slots are `EdNop` (installed by Phase F's `installEdNopFallback`).

### Task 2: Implement installAlternateOpcodes in EdOps

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt`

- [ ] **Step 1: Add the import for Im**

In `EdOps.kt`, near the top, add:
```kotlin
import ru.alepar.zx80.op.misc.Im
```

- [ ] **Step 2: Add the new install method**

In `EdOps.installInto`, insert `installAlternateOpcodes(d)` BEFORE `installEdNopFallback(d)`:

```kotlin
fun installInto(d: Decoder) {
    installRegisterTransfers(d)
    installNeg(d)
    installReturns(d)
    installRrdRld(d)
    installExtendedLdPair(d)
    installBlockMove(d)
    installBlockCompare(d)
    installSingleIo(d)
    installBlockIo(d)
    installMainTableIoStragglers(d)
    installAlternateOpcodes(d)        // NEW — Phase G WU 1
    installEdNopFallback(d)
}
```

Add the method body:

```kotlin
/**
 * Installs Z80-standard alternate ED-prefix slots for NEG/RETN/RETI/IM. Real Z80 hardware decodes
 * `010xxx100` as NEG (8 slots), `010xx0101` as RETN (4 slots), `010xx1101` as RETI (4 slots), and
 * specific patterns for IM 0/1/2 with multiple aliases each. We installed the canonical slots in
 * earlier WUs; Phase G adds the rest. Must run BEFORE installEdNopFallback so EdNop doesn't
 * overwrite the aliases.
 */
private fun installAlternateOpcodes(d: Decoder) {
    // NEG aliases (besides 0x44)
    for (slot in listOf(0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C)) {
        d.ed[slot] = Neg
    }
    // RETN aliases (besides 0x45)
    for (slot in listOf(0x55, 0x65, 0x75)) {
        d.ed[slot] = Retn
    }
    // RETI aliases (besides 0x4D)
    for (slot in listOf(0x5D, 0x6D, 0x7D)) {
        d.ed[slot] = Reti
    }
    // IM 0 aliases (besides 0x46)
    for (slot in listOf(0x4E, 0x66, 0x6E)) {
        d.ed[slot] = Im(0)
    }
    // IM 1 alias (besides 0x56)
    d.ed[0x76] = Im(1)
    // IM 2 alias (besides 0x5E)
    d.ed[0x7E] = Im(2)
}
```

- [ ] **Step 3: Run to verify pass**

Run: `./gradlew test --tests EdOpsTest`
Expected: all pass (existing + 7 new).

### Task 3: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Capture FUSE delta** (sanity check)

Run: `./build/install/zx80/bin/zx80 score --suite=fuse | head -1`
Expected: FUSE pass count climbed by ~17 (from 1335 to ~1352).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ed/EdOps.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/EdOpsTest.kt
git commit -m "feat(op/ed): install Z80-standard ED alternate slots (NEG/RETN/RETI/IM)

Phase G WU 1 of 3. Real Z80 decodes 18 alternate ED slots that we
previously masked over with EdNop:
- NEG aliases at 0x4C, 0x54, 0x5C, 0x64, 0x6C, 0x74, 0x7C
- RETN aliases at 0x55, 0x65, 0x75
- RETI aliases at 0x5D, 0x6D, 0x7D
- IM 0 aliases at 0x4E, 0x66, 0x6E
- IM 1 alias at 0x76
- IM 2 alias at 0x7E

installAlternateOpcodes runs before installEdNopFallback so EdNop
doesn't overwrite. Closes ~17 of the 21 Phase F residual failures.

Closes <BD-ID-WU-G-1>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Close beads**

Run: `bd close <BD-ID-WU-G-1>`

---

## WU G-2 — X/Y in LD A,I, LD A,R, RRD, RLD

Beads: `<BD-ID-WU-G-2>`. Add result-derived X/Y bits to the 4 ED ops with inline flag computation that Phase D missed.

### Task 1: LD A,I

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/LdAI.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/LdAITest.kt`

- [ ] **Step 1: Append failing test to LdAITest**

```kotlin
@Test
fun `LD A, I sets X and Y from result A bits 5 and 3`() {
    val cpu = Cpu().apply { i = 0x28 }
    LdAI.execute(cpu, Memory())
    // A = 0x28 -> X+Y both set
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero

    val cpu2 = Cpu().apply { i = 0x20 }
    LdAI.execute(cpu2, Memory())
    // A = 0x20 -> only X
    assertThat(cpu2.f and Flags.X).isNotZero
    assertThat(cpu2.f and Flags.Y).isZero

    val cpu3 = Cpu().apply { i = 0x08 }
    LdAI.execute(cpu3, Memory())
    // A = 0x08 -> only Y
    assertThat(cpu3.f and Flags.X).isZero
    assertThat(cpu3.f and Flags.Y).isNotZero
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdAITest`
Expected: FAIL.

- [ ] **Step 3: Update LdAI.execute**

In `src/main/kotlin/ru/alepar/zx80/op/ed/LdAI.kt`, replace:

```kotlin
override fun execute(cpu: Cpu, mem: Memory) {
    cpu.a = cpu.i and 0xFF
    var f = cpu.f and Flags.C
    if (cpu.a == 0) f = f or Flags.Z
    if (cpu.a and 0x80 != 0) f = f or Flags.S
    if (cpu.iff2) f = f or Flags.PV
    cpu.f = f
    cpu.pc = (cpu.pc + 2) and 0xFFFF
    cpu.bumpR(2)
    cpu.tStates += baseCycles
}
```

with:

```kotlin
override fun execute(cpu: Cpu, mem: Memory) {
    cpu.a = cpu.i and 0xFF
    var f = cpu.f and Flags.C
    if (cpu.a == 0) f = f or Flags.Z
    if (cpu.a and 0x80 != 0) f = f or Flags.S
    if (cpu.iff2) f = f or Flags.PV
    f = f or (cpu.a and 0x28) // X/Y from result
    cpu.f = f
    cpu.pc = (cpu.pc + 2) and 0xFFFF
    cpu.bumpR(2)
    cpu.tStates += baseCycles
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests LdAITest`
Expected: pass.

### Task 2: LD A,R

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/LdAR.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/LdARTest.kt`

- [ ] **Step 1: Append failing test to LdARTest**

```kotlin
@Test
fun `LD A, R sets X and Y from result A bits 5 and 3`() {
    val cpu = Cpu().apply { r = 0x28 }
    LdAR.execute(cpu, Memory())
    // After execute, R was bumped by 2 (so post-bump R = 0x2A) and A := R BEFORE the bump
    // Actually verify by what A becomes — depends on bump ordering. Simplest: pick I/R values where
    // X/Y bits in resulting A are predictable. Use A==R post-assignment.
    assertThat(cpu.f and Flags.X).isEqualTo(cpu.a and Flags.X)
    assertThat(cpu.f and Flags.Y).isEqualTo(cpu.a and Flags.Y)
}

@Test
fun `LD A, R X and Y match the byte loaded into A`() {
    val cpu = Cpu().apply { r = 0x07 } // r before bump; bumpR(2) makes r 0x09
    LdAR.execute(cpu, Memory())
    // Compute what A should be (post-bump, per existing impl) and check X/Y match
    val expected = cpu.a and 0x28
    assertThat(cpu.f and 0x28).isEqualTo(expected)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests LdARTest`
Expected: FAIL.

- [ ] **Step 3: Read current LdAR.kt**

Run: `cat src/main/kotlin/ru/alepar/zx80/op/ed/LdAR.kt`

The structure is similar to LdAI: A is loaded from R, flags computed inline.

- [ ] **Step 4: Update LdAR.execute**

Apply the same pattern as LdAI: locate the line `cpu.f = f` and insert immediately before it:

```kotlin
f = f or (cpu.a and 0x28) // X/Y from result
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew test --tests LdARTest`
Expected: pass.

### Task 3: RRD

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/Rrd.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/RrdTest.kt`

- [ ] **Step 1: Append failing test to RrdTest**

```kotlin
@Test
fun `RRD sets X and Y from new A bits 5 and 3`() {
    val cpu = Cpu().apply {
        a = 0x20 // high nibble keeps; low nibble becomes m's low nibble
        hl = 0x4000
    }
    val mem = Memory().apply { write(0x4000, 0x88) } // m's low nibble = 8
    Rrd.execute(cpu, mem)
    // new A = (0x20 & 0xF0) | (0x88 & 0x0F) = 0x28 -> X+Y
    assertThat(cpu.a).isEqualTo(0x28)
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests RrdTest`
Expected: FAIL.

- [ ] **Step 3: Update Rrd.execute**

In `src/main/kotlin/ru/alepar/zx80/op/ed/Rrd.kt`, find:

```kotlin
if (Flags.parity(cpu.a)) f = f or Flags.PV
cpu.f = f
```

Insert the X/Y line:

```kotlin
if (Flags.parity(cpu.a)) f = f or Flags.PV
f = f or (cpu.a and 0x28) // X/Y from result
cpu.f = f
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests RrdTest`
Expected: pass.

### Task 4: RLD

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/Rld.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/RldTest.kt`

- [ ] **Step 1: Read Rld.kt to confirm structure**

Run: `cat src/main/kotlin/ru/alepar/zx80/op/ed/Rld.kt`

The shape mirrors Rrd: A and mem[HL] are rotated; flags computed inline from new A.

- [ ] **Step 2: Append failing test to RldTest**

```kotlin
@Test
fun `RLD sets X and Y from new A bits 5 and 3`() {
    val cpu = Cpu().apply {
        a = 0x22 // high nibble keeps after the rotate
        hl = 0x4000
    }
    val mem = Memory().apply { write(0x4000, 0x88) }
    Rld.execute(cpu, mem)
    // RLD: new mem high nibble = old mem low; new mem low = old A low; new A low = old mem high
    // new A = (0x22 & 0xF0) | ((0x88 >> 4) & 0xF) = 0x20 | 0x8 = 0x28 -> X+Y
    assertThat(cpu.a).isEqualTo(0x28)
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew test --tests RldTest`
Expected: FAIL.

- [ ] **Step 4: Update Rld.execute**

Find the line setting parity in `Rld.kt`:

```kotlin
if (Flags.parity(cpu.a)) f = f or Flags.PV
cpu.f = f
```

Replace with:

```kotlin
if (Flags.parity(cpu.a)) f = f or Flags.PV
f = f or (cpu.a and 0x28) // X/Y from result
cpu.f = f
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew test --tests RldTest`
Expected: pass.

### Task 5: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Capture FUSE delta**

Run: `./build/install/zx80/bin/zx80 score`
Expected: FUSE pass count climbed by 4 from WU G-1's level (target ≥ 1356).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ed/LdAI.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/LdAR.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Rrd.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Rld.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/LdAITest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/LdARTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/RrdTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/RldTest.kt
git commit -m "feat(op/ed): X/Y bits in LD A,I/R + RRD/RLD inline flag computation

Phase G WU 2 of 3. These four ED ops compute flags inline (no
Flags.afterX helper), so Phase D's helper-level X/Y additions did not
reach them. Append result-derived X/Y to each: f or (cpu.a and 0x28).

Closes <BD-ID-WU-G-2>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Close beads**

Run: `bd close <BD-ID-WU-G-2>`

---

## WU G-3 — Sweep + tag m1-phase-g

Beads: `<BD-ID-WU-G-3>`.

### Task 1: Verification

- [ ] **Step 1: Run full suite**

Run: `./gradlew clean check installDist`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: ZEXDOC regression check**

Run: `timeout 600 ./build/install/zx80/bin/zx80 zexdoc 2>&1 | tee /tmp/zexdoc-g.log`

Expected: 0 IllegalStateException, 0 ERROR markers in `/tmp/zexdoc-g.log`. Streamed output ends with `Tests complete`.

If regression detected, STOP — Phase G introduced a break. Investigate.

- [ ] **Step 3: Capture FUSE delta + score**

Run: `./build/install/zx80/bin/zx80 score`
Expected SCORE: composite ≥ 0.965. **FUSE = 1356/1356 (100%)** — stretch target. Minimum 1352/1356 (99.7%). Capture exact line.

If FUSE < 1352, examine `build/score.json` failures. Fix or document.

- [ ] **Step 4: Apply tag**

```bash
git tag -a m1-phase-g -m "Phase G: ED alternate slots + X/Y leaks (FUSE 100% target)

18 Z80-standard ED alternate slots installed (NEG/RETN/RETI/IM aliases)
before EdNop fallback so they take precedence. X/Y bits added to four
ED ops with inline flag computation (LD A,I, LD A,R, RRD, RLD).

SCORE: <PASTE-SCORE-LINE-HERE>
FUSE pass: <X>/1356 (was 1335/1356 after Phase F)
ZEXDOC: clean, regression-free."
```

Replace placeholders with captured values.

### Task 2: Close beads + epic

- [ ] **Step 1: Close WU G-3**

Run: `bd close <BD-ID-WU-G-3> --reason="Phase G sweep complete; m1-phase-g tag applied; FUSE pass-rate <X>/1356."`

- [ ] **Step 2: Close Phase G epic**

Run: `bd close <BD-ID-EPIC-G> --reason="All 3 WUs (G-1 through G-3) closed; FUSE >=99.7%."`

---

## Self-Review

**1. Spec coverage:**

| Spec section | Plan task |
|---|---|
| NEG aliases at 7 slots | WU G-1 Task 2 |
| RETN aliases at 3 slots | WU G-1 Task 2 |
| RETI aliases at 3 slots | WU G-1 Task 2 |
| IM 0 aliases at 3 slots | WU G-1 Task 2 |
| IM 1 alias at 0x76 | WU G-1 Task 2 |
| IM 2 alias at 0x7E | WU G-1 Task 2 |
| LD A,I X/Y | WU G-2 Task 1 |
| LD A,R X/Y | WU G-2 Task 2 |
| RRD X/Y | WU G-2 Task 3 |
| RLD X/Y | WU G-2 Task 4 |
| ZEXDOC regression-clean | WU G-3 Task 1 Step 2 |
| FUSE ≥1352/1356 | WU G-3 Task 1 Step 3 |
| Tag m1-phase-g | WU G-3 Task 1 Step 4 |

All spec sections covered.

**2. Placeholder scan:** Plan uses `<BD-ID-WU-G-N>`, `<BD-ID-EPIC-G>`, `<PASTE-SCORE-LINE-HERE>`, `<X>` — runtime substitutions, documented usage.

**3. Type consistency:** `Im(n)` constructor used consistently. `Neg`, `Retn`, `Reti` are singletons (objects) — matches existing patterns. `Flags.X`, `Flags.Y` constants from earlier phases. AssertJ `isSameAs` for singleton comparisons; `isInstanceOf` + cast for `Im` since it's a class with mode parameter.

**4. Test name lint:** No backtick names contain `..`, `->`, or `;`.
