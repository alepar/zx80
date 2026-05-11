# M2.5 Keyboard Matrix + ULA IoBus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire host keyboard input to the Spectrum's 8x5 key matrix. Adds `SpectrumKey` enum, `Keyboard` (AtomicIntegerArray + refcount), `SpectrumIoBus` decoding port 0xFE, `HostKeyMap` AWT→Spectrum mapping, and SpectrumWindow `KeyListener` wiring.

**Architecture:** Three new machine-package files (SpectrumKey, Keyboard, SpectrumIoBus) plus one UI-package file (HostKeyMap). SpectrumWindow gains a `keyboard` constructor parameter and a KeyListener; SpectrumCommand wires the chain. EDT writes keyboard state, Pacer thread reads it via the AtomicIntegerArray.

**Tech Stack:** Kotlin 2.x, JUnit Jupiter 5, AssertJ, Swing/AWT (JDK 21).

**Spec:** `docs/superpowers/specs/2026-05-10-zx80-m2-5-keyboard-matrix-design.md`

**Within-phase deps:** Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 (linear, each depends on the prior).

---

## Task 1: SpectrumKey enum (M2.5-A)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/machine/SpectrumKey.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/machine/SpectrumKeyTest.kt`

### Step 1.1: Write the failing test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/machine/SpectrumKeyTest.kt`:

```kotlin
package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpectrumKeyTest {
    @Test
    fun `CAPS_SHIFT is at row 0 bit 0`() {
        assertThat(SpectrumKey.CAPS_SHIFT.row).isEqualTo(0)
        assertThat(SpectrumKey.CAPS_SHIFT.bit).isEqualTo(0)
    }

    @Test
    fun `SPACE is at row 7 bit 0`() {
        assertThat(SpectrumKey.SPACE.row).isEqualTo(7)
        assertThat(SpectrumKey.SPACE.bit).isEqualTo(0)
    }

    @Test
    fun `B is at row 7 bit 4`() {
        assertThat(SpectrumKey.B.row).isEqualTo(7)
        assertThat(SpectrumKey.B.bit).isEqualTo(4)
    }

    @Test
    fun `there are exactly 40 keys`() {
        assertThat(SpectrumKey.values()).hasSize(40)
    }
}
```

### Step 1.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.SpectrumKeyTest"
```

Expected: compilation failure (`Unresolved reference: SpectrumKey`).

### Step 1.3: Implement SpectrumKey

- [ ] Create `src/main/kotlin/ru/alepar/zx80/machine/SpectrumKey.kt`:

```kotlin
package ru.alepar.zx80.machine

/**
 * The 40 keys on the ZX Spectrum 48K rubber keyboard. Each entry pins its position in the 8x5
 * matrix. Digit keys use the prefix `K` because Kotlin enum identifiers can't start with a digit.
 *
 * Row 0: CAPS SHIFT, Z, X, C, V       (selected by clearing A8)
 * Row 1: A, S, D, F, G                (A9)
 * Row 2: Q, W, E, R, T                (A10)
 * Row 3: 1, 2, 3, 4, 5                (A11)
 * Row 4: 0, 9, 8, 7, 6                (A12)
 * Row 5: P, O, I, U, Y                (A13)
 * Row 6: ENTER, L, K, J, H            (A14)
 * Row 7: SPACE, SYMBOL SHIFT, M, N, B (A15)
 */
enum class SpectrumKey(val row: Int, val bit: Int) {
    CAPS_SHIFT(0, 0), Z(0, 1), X(0, 2), C(0, 3), V(0, 4),
    A(1, 0), S(1, 1), D(1, 2), F(1, 3), G(1, 4),
    Q(2, 0), W(2, 1), E(2, 2), R(2, 3), T(2, 4),
    K1(3, 0), K2(3, 1), K3(3, 2), K4(3, 3), K5(3, 4),
    K0(4, 0), K9(4, 1), K8(4, 2), K7(4, 3), K6(4, 4),
    P(5, 0), O(5, 1), I(5, 2), U(5, 3), Y(5, 4),
    ENTER(6, 0), L(6, 1), K(6, 2), J(6, 3), H(6, 4),
    SPACE(7, 0), SYMBOL_SHIFT(7, 1), M(7, 2), N(7, 3), B(7, 4),
}
```

### Step 1.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.SpectrumKeyTest"
```

Expected: 4 tests, all PASS.

### Step 1.5: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/machine/SpectrumKey.kt \
        src/test/kotlin/ru/alepar/zx80/machine/SpectrumKeyTest.kt
git commit -m "$(cat <<'EOF'
feat(machine): SpectrumKey enum — 40-key 8x5 keyboard matrix

Pins each Spectrum key to its (row, bit) position. Digit keys use a K
prefix (Kotlin enum identifiers can't start with a digit). Used by
Keyboard and SpectrumIoBus in the next commits.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Keyboard (M2.5-B)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/machine/Keyboard.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/machine/KeyboardTest.kt`

### Step 2.1: Write the failing test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/machine/KeyboardTest.kt`:

```kotlin
package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KeyboardTest {
    @Test
    fun `fresh keyboard reads 0x1F across all rows`() {
        val kb = Keyboard()
        assertThat(kb.read(0x00)).isEqualTo(0x1F)
    }

    @Test
    fun `press A clears row 1 bit 0`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        assertThat(kb.read(0xFD)).isEqualTo(0x1E)
    }

    @Test
    fun `release A restores row to 0x1F`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        kb.release(SpectrumKey.A)
        assertThat(kb.read(0xFD)).isEqualTo(0x1F)
    }

    @Test
    fun `refcount keeps key pressed across one early release`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        kb.press(SpectrumKey.A)
        kb.release(SpectrumKey.A)
        assertThat(kb.read(0xFD)).isEqualTo(0x1E)
    }

    @Test
    fun `refcount releases key after all presses unwound`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        kb.press(SpectrumKey.A)
        kb.release(SpectrumKey.A)
        kb.release(SpectrumKey.A)
        assertThat(kb.read(0xFD)).isEqualTo(0x1F)
    }

    @Test
    fun `read 0xFE selects only row 0`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.CAPS_SHIFT)
        kb.press(SpectrumKey.A)
        assertThat(kb.read(0xFE)).isEqualTo(0x1E)
    }

    @Test
    fun `read 0xFC ANDs rows 0 and 1`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.CAPS_SHIFT)
        assertThat(kb.read(0xFC)).isEqualTo(0x1E)
    }

    @Test
    fun `read 0xFF selects no rows and returns 0x1F`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        assertThat(kb.read(0xFF)).isEqualTo(0x1F)
    }

    @Test
    fun `release of never-pressed key is a no-op`() {
        val kb = Keyboard()
        kb.release(SpectrumKey.Z)
        assertThat(kb.read(0xFE)).isEqualTo(0x1F)
    }

    @Test
    fun `releaseAll resets all rows and refcounts`() {
        val kb = Keyboard()
        kb.press(SpectrumKey.A)
        kb.press(SpectrumKey.A)
        kb.press(SpectrumKey.B)
        kb.releaseAll()
        assertThat(kb.read(0x00)).isEqualTo(0x1F)
        kb.release(SpectrumKey.A)
        assertThat(kb.read(0xFD)).isEqualTo(0x1F)
    }
}
```

### Step 2.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.KeyboardTest"
```

Expected: compilation failure (`Unresolved reference: Keyboard`).

### Step 2.3: Implement Keyboard

- [ ] Create `src/main/kotlin/ru/alepar/zx80/machine/Keyboard.kt`:

```kotlin
package ru.alepar.zx80.machine

import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Spectrum 8x5 key matrix state, safe for concurrent read by the Pacer thread (via
 * SpectrumIoBus.read) and write by the EDT (via SpectrumWindow's KeyListener calling
 * press/release).
 *
 * Each of the 8 rows is an int in [rows]. The low 5 bits indicate state for the 5 keys: 1 =
 * released, 0 = pressed. Bits 5-7 of a row are always 1 (idle); SpectrumIoBus adds the bus
 * pull-ups for the upper bits.
 *
 * `pressCounts` refcounts each SpectrumKey so multiple host keys mapping to the same Spectrum
 * key release cleanly (e.g. Backspace = CAPS_SHIFT + K0; if the user holds Shift then taps
 * Backspace, the Shift-release should NOT clear CAPS_SHIFT while Backspace is still down).
 * `pressCounts` is EDT-only — no synchronization needed; all KeyEvents fire on the EDT, and
 * tests call press/release directly on the test thread (single-threaded JUnit execution).
 */
class Keyboard {
    private val rows = AtomicIntegerArray(8).also { for (i in 0 until 8) it.set(i, 0xFF) }
    private val pressCounts = IntArray(SpectrumKey.values().size)

    /** Press a Spectrum key. Refcounted. EDT-only. */
    fun press(key: SpectrumKey) {
        val idx = key.ordinal
        pressCounts[idx]++
        if (pressCounts[idx] == 1) {
            rows.getAndUpdate(key.row) { it and (1 shl key.bit).inv() }
        }
    }

    /** Release a Spectrum key. Refcounted. EDT-only. Safe even if never pressed. */
    fun release(key: SpectrumKey) {
        val idx = key.ordinal
        if (pressCounts[idx] == 0) return
        pressCounts[idx]--
        if (pressCounts[idx] == 0) {
            rows.getAndUpdate(key.row) { it or (1 shl key.bit) }
        }
    }

    /** Reset every refcount and row to "all keys released". Used on window focus loss. */
    fun releaseAll() {
        for (i in pressCounts.indices) pressCounts[i] = 0
        for (r in 0 until 8) rows.set(r, 0xFF)
    }

    /**
     * ULA read: high byte of port 0xFE is the row-select pattern (bit=0 means row selected).
     * Multiple rows can be selected; result is the bitwise AND of all selected rows.
     * Returns the low 5 bits only.
     */
    fun read(rowSelectByte: Int): Int {
        var result = 0xFF
        for (r in 0 until 8) {
            if ((rowSelectByte shr r) and 1 == 0) result = result and rows.get(r)
        }
        return result and 0x1F
    }
}
```

### Step 2.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.KeyboardTest"
```

Expected: 10 tests, all PASS.

### Step 2.5: Run the full test suite

- [ ] Run:

```bash
./gradlew test
```

Expected: all tests pass.

### Step 2.6: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/machine/Keyboard.kt \
        src/test/kotlin/ru/alepar/zx80/machine/KeyboardTest.kt
git commit -m "$(cat <<'EOF'
feat(machine): Keyboard — AtomicIntegerArray matrix + refcounted press/release

EDT-writer / Pacer-reader: rows live in an AtomicIntegerArray for
lock-free cross-thread reads; pressCounts is an EDT-local IntArray
refcount so multiple host keys mapping to the same Spectrum key
release cleanly. read() ANDs all rows selected by the port high byte.
releaseAll() clears every key — used on window focus loss.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: SpectrumIoBus (M2.5-C)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/machine/SpectrumIoBus.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/machine/SpectrumIoBusTest.kt`

### Step 3.1: Write the failing test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/machine/SpectrumIoBusTest.kt`:

```kotlin
package ru.alepar.zx80.machine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpectrumIoBusTest {
    @Test
    fun `read ULA port 0xFEFE returns row 0 ORed with 0xA0`() {
        val kb = Keyboard().apply { press(SpectrumKey.CAPS_SHIFT) }
        val bus = SpectrumIoBus(kb)
        assertThat(bus.read(0xFEFE)).isEqualTo(0xA0 or 0x1E)
    }

    @Test
    fun `read ULA port 0xFDFE returns row 1 ORed with 0xA0`() {
        // Port 0xFDFE: low byte 0xFE has A0=0 (ULA port); high byte 0xFD = 0b11111101,
        // bit 1 clear, so row 1 is selected.
        val kb = Keyboard().apply { press(SpectrumKey.A) }
        val bus = SpectrumIoBus(kb)
        assertThat(bus.read(0xFDFE)).isEqualTo(0xA0 or 0x1E)
    }

    @Test
    fun `read ULA port 0xFFFE (no rows) returns 0xBF`() {
        val bus = SpectrumIoBus(Keyboard())
        assertThat(bus.read(0xFFFE)).isEqualTo(0xBF)
    }

    @Test
    fun `read non-ULA port (A0=1) returns 0xFF`() {
        val bus = SpectrumIoBus(Keyboard())
        assertThat(bus.read(0xFEFF)).isEqualTo(0xFF)
    }

    @Test
    fun `write to ULA port does not throw`() {
        val bus = SpectrumIoBus(Keyboard())
        bus.write(0xFEFE, 0x07)
    }
}
```

### Step 3.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.SpectrumIoBusTest"
```

Expected: compilation failure (`Unresolved reference: SpectrumIoBus`).

### Step 3.3: Implement SpectrumIoBus

- [ ] Create `src/main/kotlin/ru/alepar/zx80/machine/SpectrumIoBus.kt`:

```kotlin
package ru.alepar.zx80.machine

import ru.alepar.zx80.cpu.IoBus

/**
 * Spectrum 48K ULA bus. Decodes Z80 IN/OUT ports:
 *
 * - Read at any port with A0=0 (ULA): low 5 bits return the keyboard matrix for the rows
 *   selected by the port high byte (bit=0 = row selected). Bits 5 and 7 return 1 (bus idle).
 *   Bit 6 returns 1 (EAR idle; M3 may override for tape input).
 * - Write at any port with A0=0 (ULA): low 3 bits set border color (M2.8); bit 4 is the
 *   beeper bit (M2.6). M2.5 stubs the write as a no-op so the CPU doesn't crash.
 * - Non-ULA ports (A0=1) read 0xFF and ignore writes (matches M1 NoIoBus behavior).
 */
class SpectrumIoBus(private val keyboard: Keyboard) : IoBus {
    override fun read(port: Int): Int =
        if ((port and 0x01) == 0) {
            val matrix = keyboard.read((port ushr 8) and 0xFF)
            matrix or 0xA0 // bit5=1 (idle), bit6=1 (EAR), bit7=1 (unused)
        } else 0xFF

    override fun write(port: Int, value: Int) {
        // Border (M2.8) and beeper (M2.6) writes land here. Today: no-op.
    }
}
```

### Step 3.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.machine.SpectrumIoBusTest"
```

Expected: 5 tests, all PASS.

### Step 3.5: Run the full test suite

- [ ] Run:

```bash
./gradlew test
```

Expected: all tests pass.

### Step 3.6: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/machine/SpectrumIoBus.kt \
        src/test/kotlin/ru/alepar/zx80/machine/SpectrumIoBusTest.kt
git commit -m "$(cat <<'EOF'
feat(machine): SpectrumIoBus — port 0xFE decode for keyboard + ULA stubs

Reads at A0=0 ports return keyboard.read(high byte) OR 0xA0
(bits 5/6/7 are idle-high; bit 6 will be EAR in M3). Writes are
no-ops today; M2.6 will wire the beeper and M2.8 will wire the
border. Non-ULA ports preserve M1 NoIoBus behavior (read 0xFF).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: HostKeyMap + SpectrumWindow KeyListener (M2.5-D)

**Files:**
- Create: `src/main/kotlin/ru/alepar/zx80/ui/HostKeyMap.kt`
- Create: `src/test/kotlin/ru/alepar/zx80/ui/HostKeyMapTest.kt`
- Modify: `src/main/kotlin/ru/alepar/zx80/ui/SpectrumWindow.kt`

### Step 4.1: Write the failing HostKeyMap test

- [ ] Create `src/test/kotlin/ru/alepar/zx80/ui/HostKeyMapTest.kt`:

```kotlin
package ru.alepar.zx80.ui

import java.awt.event.KeyEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.alepar.zx80.machine.SpectrumKey

class HostKeyMapTest {
    @Test
    fun `VK_A maps to A`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_A)).containsExactly(SpectrumKey.A)
    }

    @Test
    fun `VK_ENTER maps to ENTER`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_ENTER)).containsExactly(SpectrumKey.ENTER)
    }

    @Test
    fun `VK_SHIFT maps to CAPS_SHIFT`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_SHIFT)).containsExactly(SpectrumKey.CAPS_SHIFT)
    }

    @Test
    fun `VK_CONTROL maps to SYMBOL_SHIFT`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_CONTROL)).containsExactly(SpectrumKey.SYMBOL_SHIFT)
    }

    @Test
    fun `VK_BACK_SPACE maps to CAPS_SHIFT + K0`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_BACK_SPACE))
            .containsExactly(SpectrumKey.CAPS_SHIFT, SpectrumKey.K0)
    }

    @Test
    fun `VK_LEFT maps to CAPS_SHIFT + K5`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_LEFT))
            .containsExactly(SpectrumKey.CAPS_SHIFT, SpectrumKey.K5)
    }

    @Test
    fun `VK_F12 is unmapped`() {
        assertThat(HostKeyMap.map(KeyEvent.VK_F12)).isEmpty()
    }
}
```

### Step 4.2: Run the test and verify it fails

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.ui.HostKeyMapTest"
```

Expected: compilation failure (`Unresolved reference: HostKeyMap`).

### Step 4.3: Implement HostKeyMap

- [ ] Create `src/main/kotlin/ru/alepar/zx80/ui/HostKeyMap.kt`:

```kotlin
package ru.alepar.zx80.ui

import java.awt.event.KeyEvent
import ru.alepar.zx80.machine.SpectrumKey

/**
 * Maps an AWT [KeyEvent] VK_* code to the SpectrumKey(s) it should press. Returns an empty
 * list for unmapped keys.
 *
 * Covers letters, digits, ENTER, SPACE, the two SHIFTs, and the canonical Spectrum aliases for
 * arrows (CAPS+5/6/7/8) and DELETE (CAPS+0). Symbol keys (`;`, `:`, `[`, etc.) are not mapped —
 * typing punctuation on a Spectrum requires SYMBOL SHIFT + a specific key; users who want that
 * should hold Ctrl + the appropriate key directly.
 */
object HostKeyMap {
    fun map(keyCode: Int): List<SpectrumKey> = when (keyCode) {
        KeyEvent.VK_A -> listOf(SpectrumKey.A)
        KeyEvent.VK_B -> listOf(SpectrumKey.B)
        KeyEvent.VK_C -> listOf(SpectrumKey.C)
        KeyEvent.VK_D -> listOf(SpectrumKey.D)
        KeyEvent.VK_E -> listOf(SpectrumKey.E)
        KeyEvent.VK_F -> listOf(SpectrumKey.F)
        KeyEvent.VK_G -> listOf(SpectrumKey.G)
        KeyEvent.VK_H -> listOf(SpectrumKey.H)
        KeyEvent.VK_I -> listOf(SpectrumKey.I)
        KeyEvent.VK_J -> listOf(SpectrumKey.J)
        KeyEvent.VK_K -> listOf(SpectrumKey.K)
        KeyEvent.VK_L -> listOf(SpectrumKey.L)
        KeyEvent.VK_M -> listOf(SpectrumKey.M)
        KeyEvent.VK_N -> listOf(SpectrumKey.N)
        KeyEvent.VK_O -> listOf(SpectrumKey.O)
        KeyEvent.VK_P -> listOf(SpectrumKey.P)
        KeyEvent.VK_Q -> listOf(SpectrumKey.Q)
        KeyEvent.VK_R -> listOf(SpectrumKey.R)
        KeyEvent.VK_S -> listOf(SpectrumKey.S)
        KeyEvent.VK_T -> listOf(SpectrumKey.T)
        KeyEvent.VK_U -> listOf(SpectrumKey.U)
        KeyEvent.VK_V -> listOf(SpectrumKey.V)
        KeyEvent.VK_W -> listOf(SpectrumKey.W)
        KeyEvent.VK_X -> listOf(SpectrumKey.X)
        KeyEvent.VK_Y -> listOf(SpectrumKey.Y)
        KeyEvent.VK_Z -> listOf(SpectrumKey.Z)
        KeyEvent.VK_0 -> listOf(SpectrumKey.K0)
        KeyEvent.VK_1 -> listOf(SpectrumKey.K1)
        KeyEvent.VK_2 -> listOf(SpectrumKey.K2)
        KeyEvent.VK_3 -> listOf(SpectrumKey.K3)
        KeyEvent.VK_4 -> listOf(SpectrumKey.K4)
        KeyEvent.VK_5 -> listOf(SpectrumKey.K5)
        KeyEvent.VK_6 -> listOf(SpectrumKey.K6)
        KeyEvent.VK_7 -> listOf(SpectrumKey.K7)
        KeyEvent.VK_8 -> listOf(SpectrumKey.K8)
        KeyEvent.VK_9 -> listOf(SpectrumKey.K9)
        KeyEvent.VK_ENTER -> listOf(SpectrumKey.ENTER)
        KeyEvent.VK_SPACE -> listOf(SpectrumKey.SPACE)
        KeyEvent.VK_SHIFT -> listOf(SpectrumKey.CAPS_SHIFT)
        KeyEvent.VK_CONTROL -> listOf(SpectrumKey.SYMBOL_SHIFT)
        KeyEvent.VK_BACK_SPACE -> listOf(SpectrumKey.CAPS_SHIFT, SpectrumKey.K0)
        KeyEvent.VK_LEFT -> listOf(SpectrumKey.CAPS_SHIFT, SpectrumKey.K5)
        KeyEvent.VK_DOWN -> listOf(SpectrumKey.CAPS_SHIFT, SpectrumKey.K6)
        KeyEvent.VK_UP -> listOf(SpectrumKey.CAPS_SHIFT, SpectrumKey.K7)
        KeyEvent.VK_RIGHT -> listOf(SpectrumKey.CAPS_SHIFT, SpectrumKey.K8)
        else -> emptyList()
    }
}
```

### Step 4.4: Run the test and verify it passes

- [ ] Run:

```bash
./gradlew test --tests "ru.alepar.zx80.ui.HostKeyMapTest"
```

Expected: 7 tests, all PASS.

### Step 4.5: Modify SpectrumWindow to accept a Keyboard and install a KeyListener

- [ ] Replace the entire content of `src/main/kotlin/ru/alepar/zx80/ui/SpectrumWindow.kt` with:

```kotlin
package ru.alepar.zx80.ui

import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import ru.alepar.zx80.machine.Keyboard

/**
 * Swing-based host window that displays the Pacer's framebuffer and forwards keyboard events to
 * the Spectrum [Keyboard]. Opens a non-resizable JFrame sized 256*scale x 192*scale and spawns
 * a daemon worker thread that loops Pacer.stepOneFrame, scheduling repaints on the EDT.
 *
 * Key handling: a KeyAdapter on the focusable panel translates VK_* codes via [HostKeyMap] into
 * SpectrumKey lists, calling press/release on each. A `currentlyDown` set dedupes Java's
 * keyPressed repeats for held keys. On `windowDeactivated` and `focusLost` we call
 * `keyboard.releaseAll()` and clear the set so stuck keys are cleared when focus leaves.
 *
 * On window close: signal the pacer thread to stop, wait up to 500ms, dispose the frame, and
 * call exitProcess(0).
 */
class SpectrumWindow(
    private val pacer: Pacer,
    private val keyboard: Keyboard,
    private val scale: Int = 2,
) {
    private val frame = JFrame("ZX Spectrum 48K")
    private val panel =
        object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                g.drawImage(pacer.currentImage(), 0, 0, width, height, null)
            }
        }

    @Volatile private var running = true
    private lateinit var worker: Thread
    private val currentlyDown = mutableSetOf<Int>()

    fun show() {
        panel.preferredSize = Dimension(256 * scale, 192 * scale)
        panel.isFocusable = true
        panel.addKeyListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (!currentlyDown.add(e.keyCode)) return
                    for (key in HostKeyMap.map(e.keyCode)) keyboard.press(key)
                }

                override fun keyReleased(e: KeyEvent) {
                    if (!currentlyDown.remove(e.keyCode)) return
                    for (key in HostKeyMap.map(e.keyCode)) keyboard.release(key)
                }
            }
        )
        panel.addFocusListener(
            object : FocusAdapter() {
                override fun focusLost(e: FocusEvent) {
                    keyboard.releaseAll()
                    currentlyDown.clear()
                }
            }
        )

        frame.isResizable = false
        frame.contentPane.add(panel)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    running = false
                    worker.join(500)
                    frame.dispose()
                    exitProcess(0)
                }

                override fun windowDeactivated(e: WindowEvent) {
                    keyboard.releaseAll()
                    currentlyDown.clear()
                }
            }
        )
        worker =
            thread(isDaemon = true, name = "spectrum-pacer") {
                pacer.start()
                while (running) {
                    pacer.stepOneFrame()
                    SwingUtilities.invokeLater { panel.repaint() }
                }
            }
        frame.isVisible = true
        panel.requestFocusInWindow()
    }
}
```

### Step 4.6: Compile-only verification

The SpectrumCommand call site still uses `SpectrumWindow(pacer, scale)` (2-arg constructor) — it will not compile until Task 5 updates it. So we expect a compile error at this point.

- [ ] Run:

```bash
./gradlew compileKotlin
```

Expected: **compilation failure** in `SpectrumCommand.kt` at the line `val window = SpectrumWindow(pacer, scale)` — wrong number of arguments. This is intentional; Task 5 wires it.

### Step 4.7: Commit (skip full test suite — call site is broken until Task 5)

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/ui/HostKeyMap.kt \
        src/test/kotlin/ru/alepar/zx80/ui/HostKeyMapTest.kt \
        src/main/kotlin/ru/alepar/zx80/ui/SpectrumWindow.kt
git commit -m "$(cat <<'EOF'
feat(ui): HostKeyMap + SpectrumWindow KeyListener wiring

HostKeyMap maps AWT KeyEvent.VK_* codes to SpectrumKey lists, covering
letters/digits/essentials + arrows (CAPS+5/6/7/8) + DELETE (CAPS+0).
SpectrumWindow gains a keyboard constructor parameter and installs a
focusable-panel KeyAdapter that dedupes held-key repeats and presses
the mapped SpectrumKeys. focusLost / windowDeactivated invoke
keyboard.releaseAll() to clear stuck keys.

Note: SpectrumCommand call site is broken at this commit (2-arg
SpectrumWindow constructor no longer exists); fixed in next commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: SpectrumCommand wiring (M2.5-E)

**Files:**
- Modify: `src/main/kotlin/ru/alepar/zx80/cli/SpectrumCommand.kt`

### Step 5.1: Update SpectrumCommand to wire the keyboard chain

- [ ] Replace the entire content of `src/main/kotlin/ru/alepar/zx80/cli/SpectrumCommand.kt` with:

```kotlin
package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import ru.alepar.zx80.machine.Keyboard
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.SpectrumIoBus
import ru.alepar.zx80.machine.UlaRenderer
import ru.alepar.zx80.ui.Pacer
import ru.alepar.zx80.ui.SpectrumWindow

/** Launch the ZX Spectrum 48K emulator in a host window at integer scale. */
class SpectrumCommand : CliktCommand(name = "spectrum") {
    private val scale by option("--scale").int().default(2)

    override fun run() {
        val machine = Spectrum48k()
        val keyboard = Keyboard()
        machine.cpu.io = SpectrumIoBus(keyboard)
        machine.reset()
        val pacer = Pacer(machine, UlaRenderer())
        val window = SpectrumWindow(pacer, keyboard, scale)
        window.show()
    }
}
```

### Step 5.2: Build and verify

- [ ] Run:

```bash
./gradlew compileKotlin compileTestKotlin
```

Expected: BUILD SUCCESSFUL. The SpectrumWindow call site now uses the 3-arg constructor.

### Step 5.3: Run the full test suite

- [ ] Run:

```bash
./gradlew test
```

Expected: all tests pass — existing M1+M2.1-M2.4 + new SpectrumKey + Keyboard + SpectrumIoBus + HostKeyMap.

### Step 5.4: CLI smoke

- [ ] Run:

```bash
./gradlew installDist
./build/install/zx80/bin/zx80 spectrum --help
```

Expected: prints usage text including `--scale=INT`, exits 0, no window.

### Step 5.5: Commit

- [ ] Commit:

```bash
git add src/main/kotlin/ru/alepar/zx80/cli/SpectrumCommand.kt
git commit -m "$(cat <<'EOF'
feat(cli): wire Keyboard + SpectrumIoBus into zx80 spectrum

Constructs Keyboard, installs SpectrumIoBus(keyboard) on machine.cpu.io
before reset, passes keyboard to SpectrumWindow. Bus install before
reset is forward-compatible insurance — M1/M2.2 reset paths don't
touch I/O, but future ones might.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Sweep + tag (M2.5-F)

**Files:** none (validation + git tag + push).

### Step 6.1: Run the full check

- [ ] Run:

```bash
./gradlew clean check installDist
```

Expected: BUILD SUCCESSFUL.

### Step 6.2: Score harness regression check

- [ ] Run:

```bash
./build/install/zx80/bin/zx80 score
```

Expected:
- programs: 5/5 PASS
- fuse: 1354/1356
- ZEXDOC: 0 IllegalStateException, 0 ERROR
- composite SCORE: ≥ 0.966

If FUSE regresses, STOP. The change touches cpu.io, but only when SpectrumCommand runs — FUSE and ProgramsSuite construct their own Cpu instances with NoIoBus, so they should be unaffected.

### Step 6.3: Headless CLI gate

- [ ] Run:

```bash
./build/install/zx80/bin/zx80 spectrum --help
```

Expected: usage text, exit 0.

### Step 6.4: Manual window smoke (DISPLAY required, deferred)

This step requires a graphical session. If the executor is headless, document `headless executor — manual smoke skipped` in the closing reason and proceed.

- [ ] Run:

```bash
./build/install/zx80/bin/zx80 spectrum
```

Expected:
- Window opens (per M2.4).
- Pressing keys does not crash; the window stays responsive.
- ROM is still stuck at PC=0x11E6 (per `zx80-1qc`), so typed characters won't appear in BASIC yet. The gate here is "no crash, no freeze".
- Close via X button cleanly exits within 1 second.

Record the observed outcome for the commit message at step 6.5.

### Step 6.5: Tag, close, push

- [ ] Apply tag and close beads issue and push:

```bash
git tag -a m2-phase01-5 -m "M2.5: Keyboard matrix + ULA IoBus (port 0xFE)"
bd update zx80-unf --claim
bd close zx80-unf --reason="M2.5 complete: SpectrumKey + Keyboard (AtomicIntegerArray + refcount) + SpectrumIoBus (port 0xFE keyboard read; border/beeper write stubs) + HostKeyMap + SpectrumWindow KeyListener with releaseAll on focus-loss. Tag m2-phase01-5. SCORE preserved >=0.966. Manual smoke: <PASTE OUTCOME OR 'headless executor, skipped'>."
git pull --rebase
bd dolt push
git push
git push --tags
git status
```

Expected: `git status` shows `up to date with 'origin/opus-4.7'`. Tag pushed.

---

## Self-review notes (recorded after writing the plan)

**Spec coverage check:**

| Spec section | Task |
|---|---|
| M2.5-A SpectrumKey | Task 1 |
| M2.5-B Keyboard | Task 2 |
| M2.5-C SpectrumIoBus | Task 3 |
| M2.5-D HostKeyMap + SpectrumWindow KeyListener | Task 4 |
| M2.5-E SpectrumCommand wiring | Task 5 |
| M2.5-F Sweep + tag | Task 6 |
| Validation gates 1-9 | Task 6 (steps 6.1-6.5) plus 5.4 (CLI smoke) |

**No-placeholder check** — every step has executable code or commands. The `<PASTE OUTCOME OR 'headless executor, skipped'>` token in step 6.5's bd close reason is intended as a runtime fill-in by the executing agent, not a planning placeholder.

**Type/name consistency** — `SpectrumKey`, `Keyboard.press/release/releaseAll/read`, `SpectrumIoBus.read/write`, `HostKeyMap.map(Int): List<SpectrumKey>`, `SpectrumWindow(pacer, keyboard, scale)`, `SpectrumCommand` wiring with `machine.cpu.io = SpectrumIoBus(keyboard)` set BEFORE `machine.reset()` — used identically across spec, plan, and tests.

**Notable plan quirk:** Task 4 deliberately leaves the SpectrumCommand call site broken between the 4.7 commit and Task 5. This is called out in the commit message and Task 4 step 4.6 explicitly expects the compile error. Task 5 fixes it. This is a clean two-step rather than mixing the new HostKeyMap/KeyListener wiring with the CLI rewire in one giant commit.
