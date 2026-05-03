# Phase E — MEMPTR + Flag-Edge Quirks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lift FUSE pass rate from 1105/1356 (81.5%) to ≥95% by adding `cpu.memptr` field consumed by 5 op classes for X/Y output, plus discrete flag-edge fixes (BIT n,r PV/S quirks, SCF/CCF X/Y rule, block-op X/Y rules).

**Architecture:** `FuseSuite` already reads MEMPTR from FUSE input cases but never compares it post-execution, so we don't need to maintain MEMPTR through every Z80 op. We only need it correct at the moment X/Y is computed inside the single op under test. 5 op classes get MEMPTR-consumer logic; 1 op class gets PV/S quirk fixes; SCF/CCF get a refined X/Y rule; 8 block ops get per-op X/Y rules.

**Tech Stack:** Kotlin (JDK 21), JUnit 5, AssertJ, Gradle, Spotless ktlint.

**Spec:** `docs/superpowers/specs/2026-05-03-zx80-phase-e-memptr-and-flag-quirks-design.md`

---

## Universal patterns

**TDD per task:**
1. Append failing tests
2. Run `./gradlew test --tests <ClassName>Test` to confirm FAIL
3. Modify the class
4. Re-run to confirm PASS

**Test name rules:** avoid `..`, `->`, `;` in backtick names.

**spotlessApply** runs at the end of each WU. Reformats KDocs and `apply{}` blocks. Cosmetic only.

**Beads workflow per WU:** claim → execute → `./gradlew test spotlessApply` → commit per plan → `bd close <id>`.

**MEMPTR-consumer pattern:** for ops that consume MEMPTR for X/Y output:
```kotlin
// 1. (Optional, if op also UPDATES MEMPTR per Z80 spec) cpu.memptr = newValue
// 2. f = f or ((cpu.memptr ushr 8) and 0x28)  // X/Y from high byte
```

## File Structure

**Modified files:**
- `src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt` — add `memptr` field (WU E-1)
- `src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt` — load MEMPTR from input case (WU E-1)
- `src/main/kotlin/ru/alepar/zx80/op/bit/BitHl.kt` — consume MEMPTR for X/Y (WU E-1)
- `src/main/kotlin/ru/alepar/zx80/op/ixcb/BitIxd.kt` — set + consume MEMPTR (WU E-1)
- `src/main/kotlin/ru/alepar/zx80/op/ed/InRC.kt` — set + consume MEMPTR (WU E-1)
- `src/main/kotlin/ru/alepar/zx80/op/ed/InCFlags.kt` — set + consume MEMPTR (WU E-1)
- `src/main/kotlin/ru/alepar/zx80/op/ed/InAImm.kt` — set MEMPTR + apply X/Y to F (WU E-1)
- `src/main/kotlin/ru/alepar/zx80/op/bit/BitReg.kt` — PV mirrors Z + S quirk (WU E-2)
- `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt` — `afterScf`/`afterCcf` take `oldA` (WU E-2)
- `src/main/kotlin/ru/alepar/zx80/op/misc/Scf.kt`, `Ccf.kt` — pass `cpu.a` (WU E-2)
- `src/main/kotlin/ru/alepar/zx80/op/ed/{Ldi,Ldd,Cpi,Cpd,Ini,Ind,Outi,Outd}.kt` — block-op X/Y (WU E-3)

Test files mirror each.

---

## WU E-1 — `cpu.memptr` field + FuseSuite init + 5 op classes

Beads: `<BD-ID-WU-E-1>`. Adds the MEMPTR field, wires it from FUSE input, and updates 5 ops to consume it.

### Task 1: Add `cpu.memptr` field

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt`

- [ ] **Step 1: Add memptr field**

In `Cpu.kt`, locate the special 8-bit registers section near `var i: Int = 0` and `var r: Int = 0` (around line 38-39). Add immediately after:

```kotlin
    /**
     * MEMPTR (a.k.a. WZ) — undocumented internal 16-bit register that leaks into the X/Y flag bits
     * for BIT n,(HL), BIT n,(IX/IY+d), IN r,(C), and IN A,(n). Initialized from FUSE input cases;
     * only the 5 leaking-into-flags op classes update or consume it (Phase E scope).
     */
    var memptr: Int = 0
```

- [ ] **Step 2: Run all tests to confirm no regression**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — adding an unused field changes nothing.

### Task 2: FuseSuite loads MEMPTR

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt`

- [ ] **Step 1: Add memptr to loadFrom**

Locate the `loadFrom` extension (around line 125–149) and add `memptr = input.memptr` immediately before `tStates = 0`:

```kotlin
    private fun Cpu.loadFrom(input: FuseInputCase) {
        af = input.af
        bc = input.bc
        de = input.de
        hl = input.hl
        // ... alt regs ...
        ix = input.ix
        iy = input.iy
        sp = input.sp
        pc = input.pc
        i = input.i
        r = input.r
        iff1 = input.iff1
        iff2 = input.iff2
        im = input.im
        halted = input.halted
        memptr = input.memptr
        tStates = 0
    }
```

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

### Task 3: BitHl consumes MEMPTR

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/bit/BitHl.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/bit/BitHlTest.kt`

- [ ] **Step 1: Append failing test to BitHlTest**

```kotlin
@Test
fun `BIT n, (HL) sets X and Y from cpu memptr high byte`() {
    val cpu = Cpu().apply { hl = 0x4000; memptr = 0x2814 }
    val mem = Memory().apply { write(0x4000, 0xFF) }
    BitHl(0).execute(cpu, mem)
    // memptr_high = 0x28 -> both X and Y set
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}

@Test
fun `BIT n, (HL) X and Y come from memptr high byte even when bit-tested byte differs`() {
    val cpu = Cpu().apply { hl = 0x4000; memptr = 0x2000 }
    val mem = Memory().apply { write(0x4000, 0x28) } // mem byte has both bits
    BitHl(0).execute(cpu, mem)
    // memptr_high = 0x20 -> only X set, despite mem byte being 0x28
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isZero
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests BitHlTest`
Expected: FAIL on the new tests.

- [ ] **Step 3: Update BitHl.execute**

Replace the existing `execute` body in `BitHl.kt` with:

```kotlin
    override fun execute(cpu: Cpu, mem: Memory) {
        val bit = (mem.read(cpu.hl) shr n) and 1
        var f = cpu.f and Flags.C
        f = f or Flags.H
        if (bit == 0) f = f or (Flags.Z or Flags.PV) // PV mirrors Z (Z80 quirk)
        if (n == 7 && bit != 0) f = f or Flags.S
        f = f or ((cpu.memptr ushr 8) and 0x28) // X/Y from MEMPTR high byte
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }
```

(This also folds in the BIT-n-r PV/S quirks for the (HL) form, since the same Z80 rules apply — PV mirrors Z, S is set when n==7 and bit was set.)

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests BitHlTest`
Expected: all pass.

### Task 4: BitIxd sets + consumes MEMPTR

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ixcb/BitIxd.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ixcb/BitIxdTest.kt`

- [ ] **Step 1: Append failing tests to BitIxdTest**

```kotlin
@Test
fun `BIT n, (IX+d) sets cpu memptr to IX plus d and X-Y from memptr high byte`() {
    val cpu = Cpu().apply { ix = 0x2010; memptr = 0 }
    val mem = Memory().apply {
        write(0x102, 0x18)        // d = 0x18
        write(0x2010 + 0x18, 0xFF)
    }
    cpu.pc = 0x100
    BitIxd(IndexReg.IX, 0).execute(cpu, mem)
    // memptr should be set to (0x2010 + 0x18) = 0x2028
    assertThat(cpu.memptr).isEqualTo(0x2028)
    // X/Y from memptr_high = 0x20 -> only X
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isZero
}

@Test
fun `BIT n, (IY+d) handles negative displacement and updates memptr`() {
    val cpu = Cpu().apply { iy = 0x2828 }
    val mem = Memory().apply {
        write(0x102, 0xF0)         // d = -16
        write(0x2828 - 16, 0xFF)
    }
    cpu.pc = 0x100
    BitIxd(IndexReg.IY, 0).execute(cpu, mem)
    // memptr = 0x2818 -> X+Y -> only X (since 0x28 is X+Y but we need to check 0x18)
    // Actually 0x2828 + (-16) = 0x2818, high byte = 0x28 -> X+Y
    assertThat(cpu.memptr).isEqualTo(0x2818)
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests BitIxdTest`
Expected: FAIL.

- [ ] **Step 3: Update BitIxd.execute**

Replace the existing `execute` in `BitIxd.kt` with:

```kotlin
    override fun execute(cpu: Cpu, mem: Memory) {
        val d = mem.read(cpu.pc + 2).toByte().toInt()
        val addr = (idx.read(cpu) + d) and 0xFFFF
        cpu.memptr = addr // BIT n,(IX/IY+d) sets MEMPTR to the effective address
        val bit = (mem.read(addr) shr n) and 1
        var f = cpu.f and Flags.C
        f = f or Flags.H
        if (bit == 0) f = f or (Flags.Z or Flags.PV)
        if (n == 7 && bit != 0) f = f or Flags.S
        f = f or ((cpu.memptr ushr 8) and 0x28)
        cpu.f = f
        cpu.pc = (cpu.pc + 4) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests BitIxdTest`
Expected: all pass.

### Task 5: InRC sets + consumes MEMPTR

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/InRC.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/InRCTest.kt`

- [ ] **Step 1: Append failing test to InRCTest**

```kotlin
@Test
fun `IN r, (C) sets cpu memptr to BC plus 1 and X-Y from memptr high byte`() {
    val cpu = Cpu().apply { bc = 0x27FF; memptr = 0 }
    InRC(Reg.D).execute(cpu, Memory())
    // memptr = BC + 1 = 0x2800; high byte = 0x28 -> X+Y
    assertThat(cpu.memptr).isEqualTo(0x2800)
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}

@Test
fun `IN r, (C) wraps memptr at 16 bits`() {
    val cpu = Cpu().apply { bc = 0xFFFF }
    InRC(Reg.D).execute(cpu, Memory())
    // BC + 1 wraps to 0x0000
    assertThat(cpu.memptr).isEqualTo(0x0000)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests InRCTest`
Expected: FAIL.

- [ ] **Step 3: Update InRC.execute**

Replace the existing `execute` in `InRC.kt` with:

```kotlin
    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.memptr = (cpu.bc + 1) and 0xFFFF
        val byte = cpu.io.read(cpu.bc) and 0xFF
        dst.write(cpu, byte)
        var f = cpu.f and Flags.C
        if (byte == 0) f = f or Flags.Z
        if (byte and 0x80 != 0) f = f or Flags.S
        if (Flags.parity(byte)) f = f or Flags.PV
        f = f or (byte and 0x28) // X/Y from byte (per Z80 IN r,(C) spec — IS from result, not memptr)
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }
```

Note: `IN r,(C)` X/Y come from the BYTE READ (the input byte), per Z80 spec — NOT MEMPTR. The MEMPTR update is for downstream ops; FUSE checks F = (Z|S|PV from byte) | (X|Y from byte) | (oldC). The MEMPTR field itself is set but only X/Y use it in BIT-on-memory ops. For IN r,(C), X/Y come from the result byte itself.

(Spec section "MEMPTR consumers" was wrong on this point; the FUSE reality is that IN r,(C) X/Y are byte-derived. Plan corrects to match Z80 spec.)

- [ ] **Step 4: Update InRCTest expectations**

The first test in Step 1 above asserted X/Y from memptr_high. Change it to assert X/Y from the byte read (which the test setup needs to set up via a mock IoBus, or we use the existing default IoBus which returns 0xFF):

Replace the test added in Step 1 with:

```kotlin
@Test
fun `IN r, (C) sets cpu memptr to BC plus 1`() {
    val cpu = Cpu().apply { bc = 0x27FF; memptr = 0 }
    InRC(Reg.D).execute(cpu, Memory())
    assertThat(cpu.memptr).isEqualTo(0x2800)
}

@Test
fun `IN r, (C) sets X and Y from byte read NOT from memptr`() {
    val cpu = Cpu().apply { bc = 0x27FF; memptr = 0 }
    // default IoBus returns 0xFF -> 0xFF and 0x28 = 0x28 -> X+Y both set
    InRC(Reg.D).execute(cpu, Memory())
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew test --tests InRCTest`
Expected: all pass.

### Task 6: InCFlags sets + consumes MEMPTR

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/InCFlags.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/InCFlagsTest.kt`

InCFlags is the rrr=110 sibling of InRC: same flag math, no destination. Same MEMPTR rule.

- [ ] **Step 1: Append failing tests to InCFlagsTest**

```kotlin
@Test
fun `IN (C) sets cpu memptr to BC plus 1`() {
    val cpu = Cpu().apply { bc = 0x27FF; memptr = 0 }
    InCFlags.execute(cpu, Memory())
    assertThat(cpu.memptr).isEqualTo(0x2800)
}

@Test
fun `IN (C) sets X and Y from byte read`() {
    val cpu = Cpu().apply { bc = 0x27FF; memptr = 0 }
    InCFlags.execute(cpu, Memory())
    // default IoBus returns 0xFF -> X+Y
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests InCFlagsTest`
Expected: FAIL.

- [ ] **Step 3: Update InCFlags.execute**

Replace the existing `execute` body in `InCFlags.kt` with:

```kotlin
    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.memptr = (cpu.bc + 1) and 0xFFFF
        val byte = cpu.io.read(cpu.bc) and 0xFF
        var f = cpu.f and Flags.C
        if (byte == 0) f = f or Flags.Z
        if (byte and 0x80 != 0) f = f or Flags.S
        if (Flags.parity(byte)) f = f or Flags.PV
        f = f or (byte and 0x28)
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests InCFlagsTest`
Expected: all pass.

### Task 7: InAImm sets MEMPTR

`IN A, (n)` does NOT update flags per documented Z80. We set MEMPTR for downstream correctness but make NO F changes. If FUSE still fails on `db` after this WU, WU E-4 sweep will surface that and we triage.

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/InAImm.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/InAImmTest.kt`

- [ ] **Step 1: Append failing test to InAImmTest**

```kotlin
@Test
fun `IN A, (n) sets cpu memptr to (A shl 8 or n) plus 1`() {
    val cpu = Cpu().apply { a = 0x27; memptr = 0 }
    val mem = Memory().apply { write(0x101, 0xFF) } // n = 0xFF at pc+1
    cpu.pc = 0x100
    InAImm.execute(cpu, mem)
    // port = (0x27 shl 8) | 0xFF = 0x27FF
    // memptr = 0x27FF + 1 = 0x2800
    assertThat(cpu.memptr).isEqualTo(0x2800)
}

@Test
fun `IN A, (n) does not modify F`() {
    val cpu = Cpu().apply { a = 0x10; f = 0x55; memptr = 0 }
    val mem = Memory().apply { write(0x101, 0x00) }
    cpu.pc = 0x100
    InAImm.execute(cpu, mem)
    assertThat(cpu.f).isEqualTo(0x55)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests InAImmTest`
Expected: FAIL on the memptr assertion (existing flag-preservation test should pass since we're not changing flag behavior).

- [ ] **Step 3: Update InAImm.execute**

Replace the existing `execute` body in `InAImm.kt` with:

```kotlin
    override fun execute(cpu: Cpu, mem: Memory) {
        val n = mem.read(cpu.pc + 1)
        val port = (cpu.a shl 8) or n
        cpu.memptr = (port + 1) and 0xFFFF
        cpu.a = cpu.io.read(port) and 0xFF
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests InAImmTest`
Expected: all pass.

### Task 8: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Capture FUSE delta** (sanity check)

Run: `./build/install/zx80/bin/zx80 score --suite=fuse | head -1`
Expected: FUSE pass count climbed meaningfully (target: ~+135 from MEMPTR ops alone, i.e. ~1240/1356).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt \
        src/main/kotlin/ru/alepar/zx80/harness/suites/FuseSuite.kt \
        src/main/kotlin/ru/alepar/zx80/op/bit/BitHl.kt \
        src/main/kotlin/ru/alepar/zx80/op/ixcb/BitIxd.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/InRC.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/InCFlags.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/InAImm.kt \
        src/test/kotlin/ru/alepar/zx80/op/bit/BitHlTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ixcb/BitIxdTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/InRCTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/InCFlagsTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/InAImmTest.kt
git commit -m "feat(cpu): cpu.memptr field + FUSE init + 5 op classes consume MEMPTR

Phase E WU 1 of 4. Add cpu.memptr (Z80 hidden WZ register). FuseSuite
loads MEMPTR from each test's input case so MEMPTR consumers see the
expected initial value. Five op classes updated:
- BitHl: X/Y from MEMPTR high byte; PV mirrors Z; S set when n=7 and bit was 1
- BitIxd: sets MEMPTR to (idx + d), then X/Y from MEMPTR high byte; same PV/S quirks
- InRC: sets MEMPTR to BC+1; X/Y from byte read (Z80 IN r,(C) spec)
- InCFlags: sets MEMPTR to BC+1; X/Y from byte read
- InAImm: sets MEMPTR to (A<<8|n)+1; F unchanged (no doc'd flag updates)

Closes <BD-ID-WU-E-1>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 4: Close beads**

Run: `bd close <BD-ID-WU-E-1>`

---

## WU E-2 — `BitReg` PV/S quirks + SCF/CCF X/Y rule

Beads: `<BD-ID-WU-E-2>`. Two unrelated fixes bundled because both are small.

### Task 1: BitReg PV/S quirks

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/bit/BitReg.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/bit/BitRegTest.kt`

- [ ] **Step 1: Append failing tests to BitRegTest**

```kotlin
@Test
fun `BIT n, r mirrors PV from Z (PV set when bit is 0)`() {
    val cpu = Cpu().apply { b = 0x00 }
    BitReg(0, Reg.B).execute(cpu, Memory())
    assertThat(cpu.f and Flags.Z).isNotZero
    assertThat(cpu.f and Flags.PV).isNotZero
}

@Test
fun `BIT n, r clears PV when bit is 1 (since Z is clear)`() {
    val cpu = Cpu().apply { b = 0xFF }
    BitReg(0, Reg.B).execute(cpu, Memory())
    assertThat(cpu.f and Flags.Z).isZero
    assertThat(cpu.f and Flags.PV).isZero
}

@Test
fun `BIT 7, r sets S when bit 7 is 1`() {
    val cpu = Cpu().apply { b = 0x80 }
    BitReg(7, Reg.B).execute(cpu, Memory())
    assertThat(cpu.f and Flags.S).isNotZero
}

@Test
fun `BIT 7, r clears S when bit 7 is 0`() {
    val cpu = Cpu().apply { b = 0x00 }
    BitReg(7, Reg.B).execute(cpu, Memory())
    assertThat(cpu.f and Flags.S).isZero
}

@Test
fun `BIT n, r leaves S clear when n is not 7`() {
    val cpu = Cpu().apply { b = 0xFF }
    BitReg(3, Reg.B).execute(cpu, Memory())
    // n=3, bit-3 was 1, but S only set when n=7
    assertThat(cpu.f and Flags.S).isZero
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests BitRegTest`
Expected: FAIL.

- [ ] **Step 3: Update BitReg.execute**

Replace the existing `execute` body in `BitReg.kt` with:

```kotlin
    override fun execute(cpu: Cpu, mem: Memory) {
        val operand = src.read(cpu)
        val bit = (operand shr n) and 1
        var f = cpu.f and Flags.C
        f = f or Flags.H
        if (bit == 0) f = f or (Flags.Z or Flags.PV) // PV mirrors Z
        if (n == 7 && bit != 0) f = f or Flags.S
        f = f or (operand and 0x28) // X/Y from operand (Phase D added this; preserved)
        cpu.f = f
        cpu.pc = (cpu.pc + 2) and 0xFFFF
        cpu.bumpR(2)
        cpu.tStates += baseCycles
    }
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests BitRegTest`
Expected: all pass.

### Task 2: SCF/CCF X/Y rule with oldA parameter

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/misc/Scf.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/misc/Ccf.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/misc/ScfTest.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/misc/CcfTest.kt`

- [ ] **Step 1: Append failing tests to FlagsTest**

```kotlin
@Test
fun `afterScf computes X and Y from (oldA or newF) and 0x28 per Zilog NMOS rule`() {
    // oldA = 0x00, oldF = 0x00 -> newF before X/Y has C set (0x01); (0x00 | 0x01) and 0x28 = 0
    val f1 = Flags.afterScf(oldA = 0x00, oldF = 0x00)
    assertThat(f1 and Flags.X).isZero
    assertThat(f1 and Flags.Y).isZero

    // oldA = 0x28, oldF = 0x00 -> (0x28 | 0x01) and 0x28 = 0x28 -> X+Y
    val f2 = Flags.afterScf(oldA = 0x28, oldF = 0x00)
    assertThat(f2 and Flags.X).isNotZero
    assertThat(f2 and Flags.Y).isNotZero

    // oldA = 0x00, oldF = 0x20 (X already set) -> (0x00 | (0x20|0x01)) and 0x28 = 0x20 -> only X
    val f3 = Flags.afterScf(oldA = 0x00, oldF = 0x20)
    assertThat(f3 and Flags.X).isNotZero
    assertThat(f3 and Flags.Y).isZero
}

@Test
fun `afterCcf computes X and Y from (oldA or newF) and 0x28`() {
    // oldA = 0x28, oldF = 0x00 -> newF before X/Y is C=1 H=0 -> (0x28 | 0x01) and 0x28 = 0x28
    val f = Flags.afterCcf(oldA = 0x28, oldF = 0x00)
    assertThat(f and Flags.X).isNotZero
    assertThat(f and Flags.Y).isNotZero
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests FlagsTest`
Expected: FAIL with `Unresolved reference: oldA` (parameter not yet added).

- [ ] **Step 3: Update Flags.afterScf and afterCcf to take oldA**

In `Flags.kt`:

Replace `fun afterScf(oldF: Int): Int = (oldF and (S or Z or PV)) or C` with:

```kotlin
    /**
     * SCF: set carry flag. C=1; H=0; N=0; S/Z/PV preserved. X/Y per Zilog NMOS:
     * (oldA or newF) and 0x28.
     */
    fun afterScf(oldA: Int, oldF: Int): Int {
        var f = (oldF and (S or Z or PV)) or C
        f = f or ((oldA or f) and 0x28)
        return f
    }
```

Replace the existing `afterCcf`:

```kotlin
    fun afterCcf(oldF: Int): Int {
        val oldC = oldF and C
        var f = oldF and (S or Z or PV)
        if (oldC == 0) f = f or C // toggle to 1
        if (oldC != 0) f = f or H // H gets oldC
        return f
    }
```

with:

```kotlin
    /**
     * CCF: complement carry flag. C=!oldC; H=oldC; N=0; S/Z/PV preserved. X/Y per Zilog NMOS:
     * (oldA or newF) and 0x28.
     */
    fun afterCcf(oldA: Int, oldF: Int): Int {
        val oldC = oldF and C
        var f = oldF and (S or Z or PV)
        if (oldC == 0) f = f or C
        if (oldC != 0) f = f or H
        f = f or ((oldA or f) and 0x28)
        return f
    }
```

- [ ] **Step 4: Update Scf.execute**

In `src/main/kotlin/ru/alepar/zx80/op/misc/Scf.kt`, replace the `execute` body:

```kotlin
    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.f = Flags.afterScf(oldA = cpu.a, oldF = cpu.f)
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }
```

- [ ] **Step 5: Update Ccf.execute**

In `src/main/kotlin/ru/alepar/zx80/op/misc/Ccf.kt`, replace the `execute` body:

```kotlin
    override fun execute(cpu: Cpu, mem: Memory) {
        cpu.f = Flags.afterCcf(oldA = cpu.a, oldF = cpu.f)
        cpu.pc = (cpu.pc + 1) and 0xFFFF
        cpu.bumpR()
        cpu.tStates += baseCycles
    }
```

- [ ] **Step 6: Update existing ScfTest and CcfTest call-sites if any reference afterScf/afterCcf directly**

Run: `grep -n "afterScf\|afterCcf" src/test/kotlin/ru/alepar/zx80/op/misc/{Scf,Ccf}Test.kt`

If any tests call the old signatures (e.g. `Flags.afterScf(0x00)` directly), update them to pass an `oldA` parameter (any value works since the test asserts the integration via Scf/Ccf instances, not the helper directly).

- [ ] **Step 7: Run to verify pass**

Run: `./gradlew test --tests FlagsTest --tests ScfTest --tests CcfTest`
Expected: all pass.

### Task 3: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/bit/BitReg.kt \
        src/main/kotlin/ru/alepar/zx80/cpu/Flags.kt \
        src/main/kotlin/ru/alepar/zx80/op/misc/Scf.kt \
        src/main/kotlin/ru/alepar/zx80/op/misc/Ccf.kt \
        src/test/kotlin/ru/alepar/zx80/op/bit/BitRegTest.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/FlagsTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/misc/ScfTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/misc/CcfTest.kt
git commit -m "feat(cpu): BIT n,r PV-mirror-Z + S-on-n=7 quirk; SCF/CCF X/Y per Zilog NMOS

Phase E WU 2 of 4. Two fixes bundled:
1. BIT n,r: Z80 quirk that PV mirrors Z (always) and S is set when
   n==7 and the tested bit was 1. Closes the ~25 cb40-cb7F BIT-on-reg
   FUSE failures.
2. SCF/CCF: Zilog NMOS X/Y rule (oldA | newF) and 0x28. afterScf and
   afterCcf gain an oldA parameter; Scf/Ccf op classes pass cpu.a.
   Closes the 4 SCF/CCF FUSE failures.

Closes <BD-ID-WU-E-2>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close <BD-ID-WU-E-2>`

---

## WU E-3 — Block-op X/Y rules

Beads: `<BD-ID-WU-E-3>`. Update 8 block ops with their per-op X/Y rule. Each op computes a derived `n` byte and ORs `(n and 0x28)` into F.

The 8 single-step ops live in `src/main/kotlin/ru/alepar/zx80/op/ed/`. Each is parameterized over a "direction" sometimes (LDI/LDD have +1/-1 on HL; CPI/CPD same; INI/IND same; OUTI/OUTD same). The repeat variants (LDIR/LDDR/CPIR/CPDR/INIR/INDR/OTIR/OTDR) reuse the single-step flag math via the dispatcher loop, so they pass automatically once the single-step ops are correct.

Per Zilog NMOS spec, the X/Y rules per family:

| Family | n value (X/Y from `n and 0x28`) |
|---|---|
| LDI/LDD | `n = transferredByte + A` (low 8 bits) |
| CPI/CPD | `n = A - mem[HL] - H_after_subtract` (low 8 bits, where H_after_subtract is the H flag that came out of the comparison) |
| INI/IND | `n = portByte + ((C + 1) and 0xFF)` for INI; `n = portByte + ((C - 1) and 0xFF)` for IND |
| OUTI/OUTD | `n = portByte + L` (low 8 bits) |

### Task 1: Ldi + Ldd X/Y

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/Ldi.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/Ldd.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/LdiTest.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/LddTest.kt`

- [ ] **Step 1: Read Ldi.kt to locate the flag computation**

Run: `cat src/main/kotlin/ru/alepar/zx80/op/ed/Ldi.kt`

Identify where flags are computed inside `execute`. The change is to OR in `((byte + cpu.a) and 0x28)` — call the loaded byte `byte`. (Same pattern in Ldd.kt — code is symmetric.)

- [ ] **Step 2: Append failing test to LdiTest**

```kotlin
@Test
fun `LDI sets X and Y from (transferredByte + A) bits 5 and 3`() {
    val cpu = Cpu().apply {
        hl = 0x4000; de = 0x5000; bc = 0x0001; a = 0x10
    }
    val mem = Memory().apply { write(0x4000, 0x18) } // byte = 0x18
    Ldi.execute(cpu, mem)
    // n = byte + A = 0x18 + 0x10 = 0x28 -> X+Y
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew test --tests LdiTest`
Expected: FAIL on the new test.

- [ ] **Step 4: Update Ldi.execute**

In the existing `execute` body where `cpu.f = ...` is set, append the X/Y line. The exact change depends on the file's current shape. Locate the line that sets `cpu.f` and add immediately after:

```kotlin
        cpu.f = cpu.f or (((byte + cpu.a) and 0xFF) and 0x28)
```

(Where `byte` is the local variable holding the transferred byte. If the file uses a different name, substitute.)

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew test --tests LdiTest`
Expected: pass.

- [ ] **Step 6: Repeat for Ldd**

Append the same kind of test to `LddTest.kt`. The expected behavior is identical to LDI for X/Y (both ops use `byte + A`).

```kotlin
@Test
fun `LDD sets X and Y from (transferredByte + A) bits 5 and 3`() {
    val cpu = Cpu().apply {
        hl = 0x4000; de = 0x5000; bc = 0x0001; a = 0x20
    }
    val mem = Memory().apply { write(0x4000, 0x08) }
    Ldd.execute(cpu, mem)
    // n = 0x08 + 0x20 = 0x28 -> X+Y
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

Run: `./gradlew test --tests LddTest`
Expected: FAIL. Add `cpu.f = cpu.f or (((byte + cpu.a) and 0xFF) and 0x28)` to `Ldd.execute` at the same location. Re-run, expect PASS.

### Task 2: Cpi + Cpd X/Y

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/Cpi.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/Cpd.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/CpiTest.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/CpdTest.kt`

The Cpi/Cpd rule: `n = A - mem[HL] - H_after`. The H flag from the comparison is needed. Compute `n` from the same arithmetic the H flag is computed from.

- [ ] **Step 1: Read Cpi.kt to locate the flag computation**

Run: `cat src/main/kotlin/ru/alepar/zx80/op/ed/Cpi.kt`

Identify the local variable holding `mem[HL]` (call it `byte`) and the H flag in the new F (call it `hAfter` or rederive from `(A and 0x0F) - (byte and 0x0F)`).

- [ ] **Step 2: Append failing test to CpiTest**

```kotlin
@Test
fun `CPI sets X and Y from (A - mem[HL] - H_after) bits 5 and 3`() {
    val cpu = Cpu().apply {
        a = 0x30; hl = 0x4000; bc = 0x0001
    }
    val mem = Memory().apply { write(0x4000, 0x08) }
    Cpi.execute(cpu, mem)
    // diff = 0x30 - 0x08 = 0x28; H computed: (0x30 & 0xF) - (0x08 & 0xF) = 0 - 8 < 0 -> H=1
    // n = 0x28 - 1 = 0x27 -> X+Y? 0x27 and 0x28 = 0x20 -> only X
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isZero
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew test --tests CpiTest`
Expected: FAIL.

- [ ] **Step 4: Update Cpi.execute**

In Cpi.kt, after the existing flag computation, before `cpu.f = f`, append:

```kotlin
        val n = (cpu.a - byte - (if (f and Flags.H != 0) 1 else 0)) and 0xFF
        f = f or (n and 0x28)
```

(Substitute `byte` with whatever the local variable is named.)

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew test --tests CpiTest`
Expected: pass.

- [ ] **Step 6: Repeat for Cpd**

Add the analog test to `CpdTest.kt`:

```kotlin
@Test
fun `CPD sets X and Y from (A - mem[HL] - H_after) bits 5 and 3`() {
    val cpu = Cpu().apply {
        a = 0x30; hl = 0x4000; bc = 0x0001
    }
    val mem = Memory().apply { write(0x4000, 0x08) }
    Cpd.execute(cpu, mem)
    // n = 0x27 -> only X
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isZero
}
```

Run: `./gradlew test --tests CpdTest` -> FAIL. Add the same X/Y line to `Cpd.execute` (the H/borrow logic is identical to Cpi). Re-run, expect PASS.

### Task 3: Ini + Ind X/Y

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/Ini.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/Ind.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/IniTest.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/IndTest.kt`

INI rule: `n = portByte + ((C + 1) and 0xFF)`. IND rule: same but `(C - 1) and 0xFF`.

- [ ] **Step 1: Append failing test to IniTest**

```kotlin
@Test
fun `INI sets X and Y from (portByte + ((C + 1) and 0xFF)) bits 5 and 3`() {
    val cpu = Cpu().apply { hl = 0x4000; bc = 0x0107; b = 0x01; c = 0x07 }
    // default IoBus returns 0xFF on read; (0xFF + (0x07 + 1)) and 0xFF = (0xFF + 0x08) and 0xFF = 0x07 -> Y only?
    // 0x07 and 0x28 = 0x00 -> neither
    Ini.execute(cpu, Memory())
    assertThat(cpu.f and Flags.X).isZero
    assertThat(cpu.f and Flags.Y).isZero
}

// Better: use a TestIoBus to control the byte returned
@Test
fun `INI X and Y reflect (portByte + (C+1)) and 0x28 with controlled byte`() {
    val cpu = Cpu().apply { hl = 0x4000; bc = 0x0107 }
    // need port byte = 0x21, (C+1) = 0x08, sum = 0x29, sum and 0x28 = 0x28 -> X+Y
    // since default IoBus returns 0xFF, we'd need to inject. Skip: just verify the formula via b->0
    cpu.bc = 0x017F // C = 0x7F
    Ini.execute(cpu, Memory())
    // (0xFF + 0x80) and 0xFF = 0x7F; 0x7F and 0x28 = 0x28 -> X+Y
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew test --tests IniTest`
Expected: FAIL.

- [ ] **Step 3: Update Ini.execute**

In Ini.kt, after the existing flag computation, append:

```kotlin
        val n = (portByte + ((cpu.c + 1) and 0xFF)) and 0xFF
        f = f or (n and 0x28)
```

(Substitute `portByte` with the local variable name in the file.)

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests IniTest`
Expected: pass.

- [ ] **Step 5: Repeat for Ind**

Add analog test to `IndTest.kt`:

```kotlin
@Test
fun `IND X and Y reflect (portByte + (C-1)) and 0x28`() {
    val cpu = Cpu().apply { hl = 0x4000; bc = 0x0181 } // C = 0x81, C-1 = 0x80
    Ind.execute(cpu, Memory())
    // (0xFF + 0x80) and 0xFF = 0x7F; 0x7F and 0x28 = 0x28
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

Update Ind.execute with `n = (portByte + ((cpu.c - 1) and 0xFF)) and 0xFF; f = f or (n and 0x28)`. Re-run, expect PASS.

### Task 4: Outi + Outd X/Y

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/Outi.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/op/ed/Outd.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/OutiTest.kt`
- Modify: `src/test/kotlin/ru/alepar/zx80/op/ed/OutdTest.kt`

OUTI/OUTD rule: `n = portByte + L`, where portByte is the byte read from `mem[HL]` (the value being written to the port).

- [ ] **Step 1: Append failing test to OutiTest**

```kotlin
@Test
fun `OUTI sets X and Y from (portByte + L) bits 5 and 3`() {
    val cpu = Cpu().apply { hl = 0x4018; bc = 0x0107 }
    val mem = Memory().apply { write(0x4018, 0x10) }
    // n = 0x10 + 0x18 = 0x28 -> X+Y
    Outi.execute(cpu, mem)
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew test --tests OutiTest`
Expected: FAIL.

- [ ] **Step 3: Update Outi.execute**

Append after existing flag computation:

```kotlin
        val n = (portByte + cpu.l) and 0xFF
        f = f or (n and 0x28)
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew test --tests OutiTest`
Expected: pass.

- [ ] **Step 5: Repeat for Outd**

Add analog test to `OutdTest.kt` (same X/Y rule for OUTI and OUTD per Zilog NMOS):

```kotlin
@Test
fun `OUTD X and Y reflect (portByte + L) and 0x28`() {
    val cpu = Cpu().apply { hl = 0x4018; bc = 0x0107 }
    val mem = Memory().apply { write(0x4018, 0x10) }
    Outd.execute(cpu, mem)
    assertThat(cpu.f and Flags.X).isNotZero
    assertThat(cpu.f and Flags.Y).isNotZero
}
```

Update Outd.execute with the same OR. Re-run, expect PASS.

### Task 5: WU verification + commit

- [ ] **Step 1: Run full suite + spotless**

Run: `./gradlew test spotlessApply`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/ru/alepar/zx80/op/ed/Ldi.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Ldd.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Cpi.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Cpd.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Ini.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Ind.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Outi.kt \
        src/main/kotlin/ru/alepar/zx80/op/ed/Outd.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/LdiTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/LddTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/CpiTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/CpdTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/IniTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/IndTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/OutiTest.kt \
        src/test/kotlin/ru/alepar/zx80/op/ed/OutdTest.kt
git commit -m "feat(op/ed): block-op X/Y rules per Zilog NMOS spec

Phase E WU 3 of 4. Each block op gets its per-op X/Y rule:
- LDI/LDD: n = transferredByte + A
- CPI/CPD: n = A - mem[HL] - H_after_subtract
- INI: n = portByte + ((C + 1) and 0xFF)
- IND: n = portByte + ((C - 1) and 0xFF)
- OUTI/OUTD: n = portByte + L

X/Y come from n and 0x28. Repeat variants (LDIR/LDDR/CPIR/CPDR/INIR/
INDR/OTIR/OTDR) inherit the rule via dispatcher loop. Closes ~70 ED
block-op + IN/OUT FUSE failures.

Closes <BD-ID-WU-E-3>.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 3: Close beads**

Run: `bd close <BD-ID-WU-E-3>`

---

## WU E-4 — Sweep + tag m1-phase-e

Beads: `<BD-ID-WU-E-4>`. Validate the FUSE delta and decide on tagging.

### Task 1: Verification

- [ ] **Step 1: Run full suite**

Run: `./gradlew clean check installDist`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: ZEXDOC regression check**

Run: `timeout 600 ./build/install/zx80/bin/zx80 zexdoc 2>&1 | tee /tmp/zexdoc-e.log`

Expected: `Tests complete` line appears; `grep -c 'IllegalStateException' /tmp/zexdoc-e.log` returns 0; `grep -c 'ERROR \*\*\*\*' /tmp/zexdoc-e.log` returns 0.

If any fail, STOP — Phase E introduced a regression. Investigate.

- [ ] **Step 3: Capture FUSE delta + score**

Run: `./build/install/zx80/bin/zx80 score`
Expected SCORE: composite ≥ 0.90. FUSE pass count ≥ 1288/1356 (≥95%). Capture exact line.

If FUSE < 95%, examine `build/score.json` failures. Likely candidates:
- 1 case for `76` (HALT PC behavior) — known out of scope
- Scattered `cb` BIT or `ddcb` BIT cases that the rules above didn't catch (rare; investigate)
- Some block-op cases if the H_after-borrow or +1/-1 sign was wrong (re-derive rule and patch)

If only HALT remains: tag and complete.

If significant non-HALT residuals: STOP and report — likely a rule is slightly off in WU E-3.

- [ ] **Step 4: Apply tag**

```bash
git tag -a m1-phase-e -m "Phase E: MEMPTR (FUSE-init scope) + flag-edge quirks landed

cpu.memptr added; FuseSuite initializes from input case. 5 op classes
consume MEMPTR (BitHl, BitIxd, InRC, InCFlags, InAImm). BIT n,r PV/S
quirks fixed (PV mirrors Z; S on n=7+bit-set). SCF/CCF use Zilog NMOS
X/Y rule (oldA | newF) and 0x28. 8 block ops get per-op X/Y rules
(LDI/LDD/CPI/CPD/INI/IND/OUTI/OUTD).

SCORE: <PASTE-SCORE-LINE-HERE>
FUSE pass: <X>/1356 (was 1105/1356 after Phase D)
ZEXDOC: clean, regression-free."
```

Replace placeholders.

### Task 2: Close beads + epic

- [ ] **Step 1: Close WU E-4**

Run: `bd close <BD-ID-WU-E-4> --reason="Phase E sweep complete; m1-phase-e tag applied; FUSE pass-rate <X>/1356."`

- [ ] **Step 2: Close Phase E epic**

Run: `bd close <BD-ID-EPIC-E> --reason="All 4 WUs closed; FUSE >= 95%."`

- [ ] **Step 3: File Phase F if requested by user**

If user wants to push toward FUSE 100%, file Phase F covering: HALT PC behavior, ED unmapped slots (8-cycle NOPs), DD/FD-as-noop opcode coverage (the OpcodeCoverage gap).

Otherwise skip.

---

## Self-Review

**1. Spec coverage:**

| Spec section | Plan task |
|---|---|
| `cpu.memptr` field added | WU E-1 Task 1 |
| `FuseSuite` initializes MEMPTR | WU E-1 Task 2 |
| `BitHl` consumes MEMPTR + PV/S quirks | WU E-1 Task 3 |
| `BitIxd` sets+consumes MEMPTR + PV/S | WU E-1 Task 4 |
| `InRC` sets MEMPTR + X/Y from byte | WU E-1 Task 5 |
| `InCFlags` sets MEMPTR + X/Y from byte | WU E-1 Task 6 |
| `InAImm` sets MEMPTR (no F change) | WU E-1 Task 7 |
| `BitReg` PV mirrors Z + S on n=7 | WU E-2 Task 1 |
| `Flags.afterScf`/`afterCcf` Zilog NMOS rule | WU E-2 Task 2 |
| Block-op X/Y rules (8 ops) | WU E-3 Tasks 1-4 |
| ZEXDOC regression-clean, FUSE ≥95%, score ≥0.90 | WU E-4 Task 1 Step 3 |
| Tag `m1-phase-e` | WU E-4 Task 1 Step 4 |
| Optional Phase F filing | WU E-4 Task 2 Step 3 |

All spec sections covered. Note: spec said InRC/InCFlags X/Y come from MEMPTR, but per Z80 reference IN r,(C) X/Y come from the byte read. The plan corrects to byte-derived; if FUSE rejects this, WU E-4 sweep surfaces it for re-investigation.

**2. Placeholder scan:** Plan uses `<BD-ID-WU-E-N>`, `<BD-ID-EPIC-E>`, `<PASTE-SCORE-LINE-HERE>`, `<X>` — runtime substitutions, documented usage. The block-op tasks 2-4 use the phrase "substitute `byte`/`portByte` with the local variable name in the file" because the existing op classes' variable naming is consistent but not identical across files; this is honest direction for the executing agent rather than a placeholder gap.

**3. Type consistency:** `cpu.memptr` is `Int` everywhere. `Flags.afterScf` and `afterCcf` both have new signature `(oldA: Int, oldF: Int): Int` — matched in WU E-2 Task 2 Steps 4-5 (Scf and Ccf execute calls). Test method names use existing AssertJ + JUnit 5 patterns.

**4. Test name lint:** No backtick names contain `..`, `->`, or `;`.
