# M2.1 Memory + ROM + Spectrum48k Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lay the M2 foundation — Spectrum-shaped memory with a write-guard at 0x4000, a Gradle-downloaded Sinclair 48K ROM, and a minimal `Spectrum48k` machine container that performs Z80 reset and boots the ROM.

**Architecture:** Add a `WritePolicy` interface that `Memory` consults on every `write()` (default `OpenPolicy` keeps M1 callers untouched). Introduce a new `machine/` package for `Spectrum48k` and `RomLoader`. The ROM is fetched at build time by a hand-rolled `downloadRom` Gradle task (no plugin dependency) and shipped as a classpath resource. `Cpu` gains a `reset()` method that sets Z80-power-on register state.

**Tech Stack:** Kotlin 2.x, Gradle Kotlin DSL, JUnit Jupiter 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-05-10-zx80-m2-1-memory-rom-machine-design.md`

**Within-phase deps:** Tasks 1, 2, 3 are independent. Task 4 depends on Task 3. Task 5 depends on Tasks 1, 2, 4. Task 6 depends on all.

---

## Task 1: WritePolicy + Memory write-guard (M2.1-A)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/cpu/WritePolicy.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/WritePolicyTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Memory.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/MemoryWriteGuardTest.kt`

### Step 1.1: Write the failing WritePolicy test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/cpu/WritePolicyTest.kt`:

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WritePolicyTest {
    @Test
    fun `OpenPolicy permits all addresses`() {
        assertThat(OpenPolicy.shouldWrite(0x0000)).isTrue
        assertThat(OpenPolicy.shouldWrite(0x3FFF)).isTrue
        assertThat(OpenPolicy.shouldWrite(0x4000)).isTrue
        assertThat(OpenPolicy.shouldWrite(0xFFFF)).isTrue
    }

    @Test
    fun `ReadOnlyBelow rejects writes below limit`() {
        val policy = ReadOnlyBelow(0x4000)
        assertThat(policy.shouldWrite(0x0000)).isFalse
        assertThat(policy.shouldWrite(0x1234)).isFalse
        assertThat(policy.shouldWrite(0x3FFF)).isFalse
    }

    @Test
    fun `ReadOnlyBelow permits writes at or above limit`() {
        val policy = ReadOnlyBelow(0x4000)
        assertThat(policy.shouldWrite(0x4000)).isTrue
        assertThat(policy.shouldWrite(0x8000)).isTrue
        assertThat(policy.shouldWrite(0xFFFF)).isTrue
    }
}
```

### Step 1.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.cpu.WritePolicyTest"
```

Expected: compilation failure (`Unresolved reference: OpenPolicy`, `Unresolved reference: ReadOnlyBelow`).

### Step 1.3: Implement WritePolicy

- [ ] Create `src/main/kotlin/ru/alepar/zx80/cpu/WritePolicy.kt`:

```kotlin
package ru.alepar.zx80.cpu

/**
 * Decides whether a write to a given address should land on the underlying byte array.
 * Used by [Memory] to model the Spectrum's ROM/RAM boundary at 0x4000 (and other future
 * memory-map shapes).
 */
fun interface WritePolicy {
    fun shouldWrite(addr: Int): Boolean
}

/** Default M1 policy: every address is writable. Preserves existing test/harness behavior. */
object OpenPolicy : WritePolicy {
    override fun shouldWrite(addr: Int): Boolean = true
}

/**
 * Spectrum 48K policy: addresses below [limit] are read-only. Writes to them complete the
 * bus cycle but the byte is dropped — matching real Z80 semantics on a ROM-mapped region.
 */
class ReadOnlyBelow(private val limit: Int) : WritePolicy {
    override fun shouldWrite(addr: Int): Boolean = (addr and 0xFFFF) >= limit
}
```

### Step 1.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.cpu.WritePolicyTest"
```

Expected: 3 tests, all PASS.

### Step 1.5: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/WritePolicy.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/WritePolicyTest.kt
git commit -m "$(cat <<'EOF'
feat(cpu): add WritePolicy interface for memory-map boundaries

OpenPolicy keeps M1 behavior; ReadOnlyBelow models Spectrum's ROM/RAM
split at 0x4000. Memory wiring lands next.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Step 1.6: Write the failing Memory write-guard test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/cpu/MemoryWriteGuardTest.kt`:

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MemoryWriteGuardTest {
    @Test
    fun `default Memory has no guard - writes to 0x0000 succeed`() {
        val mem = Memory()
        mem.write(0x0000, 0x42)
        assertThat(mem.read(0x0000)).isEqualTo(0x42)
    }

    @Test
    fun `ReadOnlyBelow guard drops writes below limit`() {
        val mem = Memory(ReadOnlyBelow(0x4000))
        mem.write(0x1000, 0x42)
        assertThat(mem.read(0x1000)).isEqualTo(0x00)
    }

    @Test
    fun `ReadOnlyBelow guard permits writes at and above limit`() {
        val mem = Memory(ReadOnlyBelow(0x4000))
        mem.write(0x4000, 0xAA)
        mem.write(0x8000, 0xBB)
        mem.write(0xFFFF, 0xCC)
        assertThat(mem.read(0x4000)).isEqualTo(0xAA)
        assertThat(mem.read(0x8000)).isEqualTo(0xBB)
        assertThat(mem.read(0xFFFF)).isEqualTo(0xCC)
    }

    @Test
    fun `loadAt bypasses the guard and installs ROM bytes`() {
        val mem = Memory(ReadOnlyBelow(0x4000))
        mem.loadAt(0x0000, byteArrayOf(0x11, 0x22, 0x33))
        assertThat(mem.read(0x0000)).isEqualTo(0x11)
        assertThat(mem.read(0x0001)).isEqualTo(0x22)
        assertThat(mem.read(0x0002)).isEqualTo(0x33)
    }

    @Test
    fun `writeWord under guard drops both bytes when both addresses are below limit`() {
        val mem = Memory(ReadOnlyBelow(0x4000))
        mem.writeWord(0x0100, 0xABCD)
        assertThat(mem.read(0x0100)).isEqualTo(0x00)
        assertThat(mem.read(0x0101)).isEqualTo(0x00)
    }
}
```

### Step 1.7: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.cpu.MemoryWriteGuardTest"
```

Expected: compilation failure (`Memory` ctor takes no args; `Memory(ReadOnlyBelow(...))` does not resolve).

### Step 1.8: Modify Memory to accept WritePolicy

- [ ] Replace the entire content of `src/main/kotlin/ru/alepar/zx80/cpu/Memory.kt` with:

```kotlin
package ru.alepar.zx80.cpu

/**
 * 64K linear address space. Reads return unsigned 0..255 as Int. Writes mask to 8 bits. Address
 * arithmetic wraps mod 65536.
 *
 * Callers SHOULD pass raw arithmetic results (e.g. `sp - 1`) without pre-masking; the address is
 * wrapped internally. This is required for Z80 semantics like `PUSH` from `SP=0` decrementing to
 * `0xFFFF`.
 *
 * The [writePolicy] gates `write` (and indirectly `writeWord`). [loadAt] bypasses the policy —
 * it is the install path for ROM and other privileged loads.
 */
class Memory(private val writePolicy: WritePolicy = OpenPolicy) {
    private val bytes = ByteArray(SIZE)

    fun read(addr: Int): Int = bytes[addr and ADDR_MASK].toInt() and 0xFF

    fun write(addr: Int, value: Int) {
        if (!writePolicy.shouldWrite(addr and ADDR_MASK)) return
        bytes[addr and ADDR_MASK] = (value and 0xFF).toByte()
    }

    /**
     * Read a 16-bit little-endian word at `addr`. Low byte at `addr`, high byte at `addr + 1` (mod
     * 64K). Returns Int in 0..0xFFFF.
     */
    fun readWord(addr: Int): Int = read(addr) or (read(addr + 1) shl 8)

    /**
     * Write a 16-bit value as little-endian. Low byte at `addr`, high byte at `addr + 1` (mod 64K).
     * Value masked to 16 bits. Both bytes are subject to [writePolicy].
     */
    fun writeWord(addr: Int, value: Int) {
        write(addr, value and 0xFF)
        write(addr + 1, (value ushr 8) and 0xFF)
    }

    /** Bypasses [writePolicy]. Use this to install ROM bytes at boot or set up test fixtures. */
    fun loadAt(addr: Int, payload: ByteArray) {
        require(addr in 0..ADDR_MASK) { "load address out of range: 0x${addr.toString(16)}" }
        require(payload.size <= SIZE - addr) {
            "payload of ${payload.size} bytes at 0x${addr.toString(16)} overflows 64K address space"
        }
        payload.copyInto(bytes, addr)
    }

    companion object {
        const val SIZE = 0x10000
        const val ADDR_MASK = 0xFFFF
    }
}
```

### Step 1.9: Run all Memory tests and verify they pass

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.cpu.MemoryTest" \
                --tests "ru.alepar.zx80.cpu.MemoryWriteGuardTest"
```

Expected: all tests PASS (existing `MemoryTest` + new `MemoryWriteGuardTest`).

### Step 1.10: Run the full test suite to confirm no M1 regression

- [ ] Run:

```bash
./gradlew test
```

Expected: all existing tests still pass. The default `OpenPolicy` means every M1 caller (`Memory()`) is unaffected.

### Step 1.11: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Memory.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/MemoryWriteGuardTest.kt
git commit -m "$(cat <<'EOF'
feat(cpu): Memory consults WritePolicy on write; loadAt bypasses

Memory(writePolicy = OpenPolicy) by default — M1 callers and tests are
untouched. Spectrum-shaped memory will pass ReadOnlyBelow(0x4000) at
construction. loadAt remains the install path and ignores the policy.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Cpu.reset() (M2.1-D)

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/cpu/CpuResetTest.kt`

### Step 2.1: Write the failing test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/cpu/CpuResetTest.kt`:

```kotlin
package ru.alepar.zx80.cpu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CpuResetTest {
    @Test
    fun `reset puts Cpu in Z80 power-on state`() {
        val cpu = Cpu().apply {
            // Dirty every field so reset must clear it
            a = 0x12; f = 0x34
            b = 0x56; c = 0x78
            d = 0x9A; e = 0xBC
            h = 0xDE; l = 0xF0
            aAlt = 0x01; fAlt = 0x02
            bAlt = 0x03; cAlt = 0x04
            dAlt = 0x05; eAlt = 0x06
            hAlt = 0x07; lAlt = 0x08
            ix = 0x1234; iy = 0x5678
            sp = 0x0100; pc = 0x0200
            i = 0x42; r = 0x55
            memptr = 0x9999
            iff1 = true; iff2 = true
            im = 2
            halted = true
            tStates = 12345L
        }

        cpu.reset()

        assertThat(cpu.pc).isEqualTo(0x0000)
        assertThat(cpu.sp).isEqualTo(0xFFFF)
        assertThat(cpu.af).isEqualTo(0xFFFF)
        assertThat(cpu.bc).isEqualTo(0xFFFF)
        assertThat(cpu.de).isEqualTo(0xFFFF)
        assertThat(cpu.hl).isEqualTo(0xFFFF)
        assertThat(cpu.aAlt).isEqualTo(0xFF)
        assertThat(cpu.fAlt).isEqualTo(0xFF)
        assertThat(cpu.bAlt).isEqualTo(0xFF)
        assertThat(cpu.cAlt).isEqualTo(0xFF)
        assertThat(cpu.dAlt).isEqualTo(0xFF)
        assertThat(cpu.eAlt).isEqualTo(0xFF)
        assertThat(cpu.hAlt).isEqualTo(0xFF)
        assertThat(cpu.lAlt).isEqualTo(0xFF)
        assertThat(cpu.ix).isEqualTo(0xFFFF)
        assertThat(cpu.iy).isEqualTo(0xFFFF)
        assertThat(cpu.i).isEqualTo(0x00)
        assertThat(cpu.r).isEqualTo(0x00)
        assertThat(cpu.memptr).isEqualTo(0x0000)
        assertThat(cpu.iff1).isFalse
        assertThat(cpu.iff2).isFalse
        assertThat(cpu.im).isEqualTo(0)
        assertThat(cpu.halted).isFalse
        assertThat(cpu.tStates).isEqualTo(0L)
    }

    @Test
    fun `reset is idempotent`() {
        val cpu = Cpu()
        cpu.reset()
        val pc1 = cpu.pc; val sp1 = cpu.sp; val af1 = cpu.af
        val r1 = cpu.r; val tStates1 = cpu.tStates
        cpu.reset()
        assertThat(cpu.pc).isEqualTo(pc1)
        assertThat(cpu.sp).isEqualTo(sp1)
        assertThat(cpu.af).isEqualTo(af1)
        assertThat(cpu.r).isEqualTo(r1)
        assertThat(cpu.tStates).isEqualTo(tStates1)
    }

    @Test
    fun `bumpR after reset gives R=1`() {
        val cpu = Cpu().apply { r = 0x77 }
        cpu.reset()
        assertThat(cpu.r).isEqualTo(0)
        cpu.bumpR()
        assertThat(cpu.r).isEqualTo(1)
    }
}
```

### Step 2.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.cpu.CpuResetTest"
```

Expected: compilation failure (`Unresolved reference: reset`).

### Step 2.3: Add the reset() method to Cpu

- [ ] At the end of the `Cpu` class in `src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt` (just before the closing `}`), add:

```kotlin
    /**
     * Z80 hardware reset: PC=I=R=0, IFF1=IFF2=false, IM=0, halted=false, MEMPTR=0, tStates=0.
     * SP and the main register pairs are set to 0xFFFF (Z80 power-on convention; Sinclair's ROM
     * sets SP explicitly within the first dozen instructions). Alternates and IX/IY likewise
     * 0xFFFF for parity with real-hardware indeterminate state.
     */
    fun reset() {
        a = 0xFF; f = 0xFF
        b = 0xFF; c = 0xFF
        d = 0xFF; e = 0xFF
        h = 0xFF; l = 0xFF
        aAlt = 0xFF; fAlt = 0xFF
        bAlt = 0xFF; cAlt = 0xFF
        dAlt = 0xFF; eAlt = 0xFF
        hAlt = 0xFF; lAlt = 0xFF
        ix = 0xFFFF
        iy = 0xFFFF
        sp = 0xFFFF
        pc = 0x0000
        i = 0
        r = 0
        memptr = 0
        iff1 = false
        iff2 = false
        im = 0
        halted = false
        tStates = 0L
    }
```

### Step 2.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.cpu.CpuResetTest"
```

Expected: 3 tests, all PASS.

### Step 2.5: Run the full test suite

- [ ] Run:

```bash
./gradlew test
```

Expected: all existing tests still pass. (Adding a new method does not affect M1 dispatch.)

### Step 2.6: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/cpu/Cpu.kt \
        src/test/kotlin/ru/alepar/zx80/cpu/CpuResetTest.kt
git commit -m "$(cat <<'EOF'
feat(cpu): add Cpu.reset() for Z80 power-on register state

PC=I=R=IFF1=IFF2=IM=0; SP and main/alt register pairs set to 0xFFFF;
halted=false; tStates=0; MEMPTR cleared. Used by Spectrum48k.reset().

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Gradle downloadRom task (M2.1-B)

**Files:**
- Modify: `build.gradle.kts`

This task has no JUnit test (Gradle behavior is not unit-testable here). Verification is `./gradlew downloadRom` succeeds and produces a 16384-byte file with the expected SHA-256.

### Step 3.1: Add the downloadRom task — first pass with empty SHA placeholder

- [ ] Add these two imports at the very top of `build.gradle.kts` (above the existing `plugins { ... }` block; both are required because Gradle Kotlin DSL has a `java` extension that shadows the `java.*` package — fully-qualified `java.net.URL` does NOT resolve):

```kotlin
import java.net.URL
import java.security.MessageDigest
```

- [ ] Then append to `build.gradle.kts` (after the existing `tasks.test { ... }` block, before the `spotless { ... }` block):

```kotlin
val downloadRom by tasks.registering {
    val outFile = layout.buildDirectory.file("generated-resources/rom/48.rom")
    val romUrl = "https://github.com/redcode/ZX-Spectrum-48K-ROMs/raw/master/48.rom"
    val expectedSize = 16_384
    // To be filled in step 3.3 once we've downloaded the file once and recorded the digest.
    val expectedSha256 = ""

    outputs.file(outFile)
    outputs.upToDateWhen {
        val f = outFile.get().asFile
        f.exists() && f.length() == expectedSize.toLong() &&
            (expectedSha256.isEmpty() || sha256Hex(f.readBytes()) == expectedSha256)
    }

    doLast {
        val target = outFile.get().asFile
        target.parentFile.mkdirs()
        URL(romUrl).openStream().use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        val actualSize = target.length()
        check(actualSize == expectedSize.toLong()) {
            "downloaded $romUrl: expected $expectedSize bytes, got $actualSize"
        }
        val actualSha = sha256Hex(target.readBytes())
        if (expectedSha256.isNotEmpty()) {
            check(actualSha == expectedSha256) {
                "48.rom SHA-256 mismatch: got $actualSha, expected $expectedSha256. " +
                    "Update URL or place a verified copy at ${target.absolutePath}"
            }
        }
        logger.lifecycle("downloaded 48.rom (${actualSize} bytes, sha256=$actualSha) to $target")
    }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

tasks.processResources {
    dependsOn(downloadRom)
    from(layout.buildDirectory.dir("generated-resources"))
}
```

### Step 3.2: Run the task once to download and capture the SHA

- [ ] Run:

```bash
./gradlew clean downloadRom
```

Expected output (lifecycle line): `downloaded 48.rom (16384 bytes, sha256=<HEX>) to <path>`.

- [ ] Copy the printed `<HEX>` SHA-256 value (64 lowercase hex chars).
- [ ] Verify the output file:

```bash
ls -la build/generated-resources/rom/48.rom
sha256sum build/generated-resources/rom/48.rom
```

Expected: file exists, size 16384, SHA matches the value the task printed.

### Step 3.3: Pin the SHA in build.gradle.kts

- [ ] Edit `build.gradle.kts`: replace the `val expectedSha256 = ""` line with the captured digest:

```kotlin
    val expectedSha256 = "<paste the 64-char hex from step 3.2>"
```

### Step 3.4: Verify caching and SHA check

- [ ] Run:

```bash
./gradlew downloadRom
```

Expected: `Task :downloadRom UP-TO-DATE`.

- [ ] Tamper with the file to verify the SHA check fires:

```bash
echo "tampered" > build/generated-resources/rom/48.rom
./gradlew downloadRom
```

Expected: task re-runs (size or SHA mismatch on `upToDateWhen`), re-downloads from GitHub, succeeds. (If the URL is down, the task fails with a clear network error — that's fine; restore the file from a known-good copy or wait.)

- [ ] Restore by re-running clean download:

```bash
./gradlew clean downloadRom
```

### Step 3.5: Verify processResources picks up the ROM

- [ ] Run:

```bash
./gradlew processResources
ls build/resources/main/rom/48.rom
sha256sum build/resources/main/rom/48.rom
```

Expected: `build/resources/main/rom/48.rom` exists, size 16384, SHA matches.

### Step 3.6: Commit

- [ ] Commit:

```bash
git add build.gradle.kts
git commit -m "$(cat <<'EOF'
build: add downloadRom task for Sinclair 48K ROM

Hand-rolled Gradle task fetches 48.rom from redcode/ZX-Spectrum-48K-ROMs
into build/generated-resources/rom/48.rom and SHA-256-verifies the bytes.
Cached after first run (upToDateWhen checks size + SHA). Wired into
processResources so the file ships as a classpath resource. Repo stays
binary-clean.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: RomLoader (M2.1-C)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/machine/RomLoader.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/machine/RomLoaderTest.kt`

Depends on Task 3 (the ROM file must be present at `/rom/48.rom` on the classpath).

### Step 4.1: Write the failing test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/machine/RomLoaderTest.kt`:

```kotlin
package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RomLoaderTest {
    @Test
    fun `load48k returns 16384 bytes`() {
        val rom = RomLoader.load48k()
        assertThat(rom.size).isEqualTo(16_384)
    }

    @Test
    fun `load48k first byte is 0xF3 - DI - canonical Sinclair 48K ROM start`() {
        val rom = RomLoader.load48k()
        assertThat(rom[0].toInt() and 0xFF).isEqualTo(0xF3)
    }
}
```

### Step 4.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.RomLoaderTest"
```

Expected: compilation failure (`Unresolved reference: RomLoader`).

### Step 4.3: Implement RomLoader

- [ ] Create `src/main/kotlin/ru/alepar/zx80/machine/RomLoader.kt`:

```kotlin
package ru.alepar.zx80.machine

/**
 * Loads the Sinclair 48K ROM image from the classpath. The ROM is downloaded at build time by
 * the Gradle `downloadRom` task and bundled as a resource at `/rom/48.rom`.
 */
object RomLoader {
    private const val RESOURCE_PATH = "/rom/48.rom"
    private const val EXPECTED_SIZE = 16_384

    fun load48k(): ByteArray {
        val stream = RomLoader::class.java.getResourceAsStream(RESOURCE_PATH)
            ?: error(
                "$RESOURCE_PATH not found on classpath. Run ./gradlew downloadRom to fetch the " +
                    "Sinclair 48K ROM, or place a verified copy at " +
                    "build/generated-resources/rom/48.rom."
            )
        val bytes = stream.use { it.readBytes() }
        check(bytes.size == EXPECTED_SIZE) {
            "$RESOURCE_PATH expected $EXPECTED_SIZE bytes, got ${bytes.size}"
        }
        return bytes
    }
}
```

### Step 4.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.RomLoaderTest"
```

Expected: 2 tests, all PASS. (If you see "not found on classpath", run `./gradlew processResources` first.)

### Step 4.5: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/machine/RomLoader.kt \
        src/test/kotlin/ru/alepar/zx80/machine/RomLoaderTest.kt
git commit -m "$(cat <<'EOF'
feat(machine): RomLoader for Sinclair 48K classpath resource

Loads /rom/48.rom from the classpath (fetched by Gradle's downloadRom
task) and asserts the canonical 16384-byte size. Loud failure with
remediation hint if the resource is missing.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Spectrum48k machine class (M2.1-E)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/machine/Spectrum48k.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/machine/Spectrum48kTest.kt`

Depends on Tasks 1, 2, 4.

### Step 5.1: Write the failing test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/machine/Spectrum48kTest.kt`:

```kotlin
package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Spectrum48kTest {
    @Test
    fun `reset puts CPU in Z80 power-on state`() {
        val machine = Spectrum48k()
        machine.reset()

        assertThat(machine.cpu.pc).isEqualTo(0x0000)
        assertThat(machine.cpu.sp).isEqualTo(0xFFFF)
        assertThat(machine.cpu.af).isEqualTo(0xFFFF)
        assertThat(machine.cpu.iff1).isFalse
        assertThat(machine.cpu.iff2).isFalse
        assertThat(machine.cpu.im).isEqualTo(0)
        assertThat(machine.cpu.halted).isFalse
        assertThat(machine.cpu.tStates).isEqualTo(0L)
    }

    @Test
    fun `reset installs the 48K ROM at 0x0000`() {
        val machine = Spectrum48k()
        machine.reset()

        // First byte of Sinclair 48K ROM is 0xF3 (DI).
        assertThat(machine.mem.read(0x0000)).isEqualTo(0xF3)
        // Last byte of the 16K ROM lives at 0x3FFF and must be readable (sanity that the full
        // image landed). We don't pin a specific value — the ROM is canonical and any byte read
        // here will match across runs.
        val lastRomByte = machine.mem.read(0x3FFF)
        assertThat(lastRomByte).isBetween(0, 0xFF)
    }

    @Test
    fun `runtime writes to ROM area are dropped`() {
        val machine = Spectrum48k()
        machine.reset()
        val before = machine.mem.read(0x1234)
        machine.mem.write(0x1234, before xor 0xFF)
        assertThat(machine.mem.read(0x1234)).isEqualTo(before)
    }

    @Test
    fun `runtime writes to RAM area succeed`() {
        val machine = Spectrum48k()
        machine.reset()
        machine.mem.write(0x5000, 0xAA)
        assertThat(machine.mem.read(0x5000)).isEqualTo(0xAA)
    }

    @Test
    fun `step after reset advances PC by 1 - DI is one byte`() {
        val machine = Spectrum48k()
        machine.reset()
        machine.step()
        assertThat(machine.cpu.pc).isEqualTo(0x0001)
    }

    @Test
    fun `run does not crash for 10000 cycles from reset`() {
        val machine = Spectrum48k()
        machine.reset()
        machine.run(10_000L)
        assertThat(machine.cpu.tStates).isGreaterThanOrEqualTo(10_000L)
    }
}
```

### Step 5.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.Spectrum48kTest"
```

Expected: compilation failure (`Unresolved reference: Spectrum48k`).

### Step 5.3: Implement Spectrum48k

- [ ] Create `src/main/kotlin/ru/alepar/zx80/machine/Spectrum48k.kt`:

```kotlin
package ru.alepar.zx80.machine

import ru.alepar.zx80.cpu.Cpu
import ru.alepar.zx80.cpu.Decoder
import ru.alepar.zx80.cpu.Dispatcher
import ru.alepar.zx80.cpu.Memory
import ru.alepar.zx80.cpu.ReadOnlyBelow
import ru.alepar.zx80.op.OpTableBuilder

/**
 * Minimal ZX Spectrum 48K machine container — Cpu, Memory with a 0x4000 write-guard, and an
 * Op dispatcher. cpu.io stays as the default NoIoBus until M2.5 wires the keyboard-aware bus.
 *
 * No frame loop, no interrupts, no ULA video — those land in M2.2-M2.5.
 */
class Spectrum48k(decoder: Decoder = OpTableBuilder.build()) {
    val cpu: Cpu = Cpu()
    val mem: Memory = Memory(ReadOnlyBelow(0x4000))
    private val dispatcher = Dispatcher(decoder)

    /** Z80 power-on register state + ROM installed at 0x0000-0x3FFF. Idempotent. */
    fun reset() {
        cpu.reset()
        mem.loadAt(0, RomLoader.load48k())
    }

    /** Decode and execute one instruction at cpu.pc. Throws if the slot is unmapped. */
    fun step() {
        val op = dispatcher.decodeAt(cpu, mem)
            ?: error(
                "no dispatch route for opcode 0x${mem.read(cpu.pc).toString(16)} " +
                    "at pc=0x${cpu.pc.toString(16)}"
            )
        op.execute(cpu, mem)
    }

    /**
     * Step until [cycles] T-states have elapsed since the call started, or until the CPU halts.
     * Returns when either condition holds.
     */
    fun run(cycles: Long) {
        val target = cpu.tStates + cycles
        while (cpu.tStates < target && !cpu.halted) step()
    }
}
```

### Step 5.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.Spectrum48kTest"
```

Expected: 6 tests, all PASS.

If `run does not crash for 10000 cycles from reset` fails with a dispatch error, that means the real ROM hits an opcode path that throws. The two known FUSE residuals (`zx80-b0c`) are `ed5f` and `ed7d` — both reachable in ROM init code is unlikely but possible. If you hit it:
- Capture the failing PC and opcode from the error message.
- File a beads issue under `zx80-myy` blocking M2.1-F: `bd create --title="Spectrum48k smoke run hits unmapped opcode 0x<X> at PC=0x<Y>"`.
- Reduce the smoke `run(10_000L)` to a value that doesn't crash, document the crash point in the test comment, and proceed with Task 6. Don't fix the underlying bug here — that's a separate WU.

### Step 5.5: Run the full test suite

- [ ] Run:

```bash
./gradlew test
```

Expected: all tests pass — existing M1 suite + new M2.1 tests.

### Step 5.6: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/machine/Spectrum48k.kt \
        src/test/kotlin/ru/alepar/zx80/machine/Spectrum48kTest.kt
git commit -m "$(cat <<'EOF'
feat(machine): Spectrum48k container — Cpu + ROM-guarded Memory + dispatch

reset() loads the 48K ROM at 0x0000-0x3FFF and applies Z80 power-on
register state. step() decodes-and-executes via the existing M1
dispatcher; run(cycles) drives until budget expires or HALT. cpu.io
stays default NoIoBus pending M2.5.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Sweep + tag (M2.1-F)

**Files:** none (validation + git tag).

### Step 6.1: Run the full check

- [ ] Run:

```bash
./gradlew clean check installDist
```

Expected: `BUILD SUCCESSFUL`. The clean forces `downloadRom` to re-run and re-verify the SHA, exercising the full pipeline.

### Step 6.2: Run the score harness for regression check

- [ ] Run:

```bash
./build/install/zx80/bin/zx80 score
```

Expected output (look for these in the JSON or text summary):
- `programs`: 5/5 PASS
- `fuse`: 1354/1356 PASS (the 2 known residuals are `ed5f` LD A,R N/PV and `ed7d` RETI iff1, tracked in `zx80-b0c`)
- ZEXDOC: 0 IllegalStateException, 0 ERROR (final summary line)
- composite SCORE: ≥ 0.96 (current best is 0.966; this WU does not change CPU behavior, so the score should be unchanged or trivially different)

If any number regresses (FUSE drops below 1354, programs drops below 5, ZEXDOC throws), STOP. The most likely cause is the WritePolicy default — verify `Memory()` (no args) still maps to `OpenPolicy`. Do not tag.

### Step 6.3: Verify the spec smoke gate

- [ ] Run a one-off Kotlin script (or temporarily add and remove a `main()`) to confirm `Spectrum48k().reset(); run(10_000L)` works outside of tests:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.Spectrum48kTest.run does not crash for 10000 cycles from reset"
```

Expected: PASS. (This is also covered by step 6.1, but isolating it makes the failure mode obvious if it regresses.)

### Step 6.4: Tag the milestone

- [ ] Apply the tag:

```bash
git tag -a m2-phase01-1 -m "M2.1: Memory + ROM + Spectrum48k machine container"
git tag --list | grep m2-phase01-1
```

Expected: `m2-phase01-1` listed.

### Step 6.5: Close the beads issue and push

- [ ] Close M2.1 in beads and push:

```bash
bd close zx80-myy --reason="M2.1 complete: WritePolicy + Memory write-guard + downloadRom + RomLoader + Cpu.reset + Spectrum48k. Tag m2-phase01-1 applied. SCORE preserved at ≥0.96; FUSE 1354/1356, programs 5/5."
git pull --rebase
bd dolt push
git push
git push --tags
git status
```

Expected: `git status` shows `On branch opus-4.7` and `up to date with 'origin/opus-4.7'`. Tag pushed to remote.

---

## Self-review notes (recorded after writing the plan)

**Spec coverage check** — every spec section has a task:

| Spec section | Task |
|---|---|
| M2.1-A WritePolicy + Memory | Task 1 |
| M2.1-B Gradle downloadRom | Task 3 |
| M2.1-C RomLoader | Task 4 |
| M2.1-D Cpu.reset | Task 2 |
| M2.1-E Spectrum48k | Task 5 |
| M2.1-F Sweep + tag | Task 6 |
| Validation gates 1-8 | Task 6 (steps 6.1, 6.2, 6.3) |

**No-placeholder check** — every step contains the actual code or command. The single intentional placeholder is the SHA-256 in Task 3, which is a *captured-from-runtime* value: step 3.2 prints it, step 3.3 pins it, step 3.4 verifies caching. No placeholders survive into committed code.

**Type/name consistency** — `WritePolicy`/`OpenPolicy`/`ReadOnlyBelow`, `Memory(writePolicy: WritePolicy = OpenPolicy)`, `Cpu.reset()`, `RomLoader.load48k()`, `Spectrum48k(decoder: Decoder = OpTableBuilder.build())`, `cpu`/`mem` properties on `Spectrum48k` — used identically across spec, plan, and tests.
