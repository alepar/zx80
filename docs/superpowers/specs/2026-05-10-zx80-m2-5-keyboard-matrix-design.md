# M2.5: Keyboard Matrix + ULA IoBus — Design

## Goal

Wire host keyboard input to the Spectrum's 8×5 key matrix. A
`Keyboard` class holds matrix state in an `AtomicIntegerArray(8)`, a
`SpectrumIoBus` decodes ULA port reads to keyboard lookups (writes are
stubbed for M2.6/M2.8), and a Swing `KeyListener` on `SpectrumWindow`
translates host `KeyEvent`s through a `HostKeyMap` to Keyboard methods.

## Context

Beads issue: `zx80-unf` (parent: `zx80-48f` M2 epic).

After M2.4, the host window opens and the Pacer drives `runFrame()`.
The CPU's IoBus is still `NoIoBus` — IN reads return 0xFF and OUT
writes are dropped. The ROM polls the keyboard matrix every frame; with
NoIoBus it sees "no keys pressed", which is fine but means the user
can't interact.

M2.5 implements the ULA's keyboard side: read port 0xFE returns the
matrix state for the selected rows. Writes to port 0xFE will carry
border color (M2.8) and beeper state (M2.6); M2.5 stubs the write side
as a no-op so the CPU doesn't crash, leaving the slots for the next two
WUs.

The Spectrum keyboard hardware: 8 rows × 5 keys per row, scanned by
writing a row-select pattern to the high byte of port 0xFE (bit clear =
row selected; multiple rows can be active simultaneously, in which case
reads return the bitwise AND of all selected rows). Each row's 5 bits
indicate state: 0 = pressed, 1 = released.

## Scope

### M2.5-A: SpectrumKey enum

New `machine/SpectrumKey.kt`:

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

### M2.5-B: Keyboard class

New `machine/Keyboard.kt`:

```kotlin
package ru.alepar.zx80.machine

import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Spectrum 8x5 key matrix state, safe for concurrent read by the Pacer thread (via SpectrumIoBus
 * -> read) and write by the EDT (via SpectrumWindow's KeyListener -> press/release).
 *
 * Each of the 8 rows is an int in [rows]. The low 5 bits indicate state for the 5 keys: 1 =
 * released, 0 = pressed. Bits 5-7 of a row int are always 1 (idle high; bus pulls them up).
 *
 * `pressCounts` refcounts each SpectrumKey so multiple host keys mapping to the same Spectrum key
 * release cleanly (e.g. backspace = CAPS_SHIFT + K0; if the user holds Shift then taps Backspace,
 * the Shift-release should NOT clear CAPS_SHIFT while Backspace is still down). pressCounts is
 * EDT-only — no synchronization needed; all KeyEvents fire on the EDT.
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

    /**
     * Release every key (refcounts to 0, rows all 0xFF). Used by SpectrumWindow on
     * `windowDeactivated`/`focusLost` to avoid stuck keys when the host loses focus.
     */
    fun releaseAll() {
        for (i in pressCounts.indices) pressCounts[i] = 0
        for (r in 0 until 8) rows.set(r, 0xFF)
    }

    /**
     * ULA read: high byte of port 0xFE is the row-select pattern (bit=0 means row selected).
     * Multiple rows can be selected; result is the bitwise AND of all selected rows.
     * Returns the low 5 bits only (bus pull-ups for bits 5-7 are added by SpectrumIoBus).
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

### M2.5-C: SpectrumIoBus

New `machine/SpectrumIoBus.kt`:

```kotlin
package ru.alepar.zx80.machine

import ru.alepar.zx80.cpu.IoBus

/**
 * The Spectrum 48K ULA bus. Decodes Z80 IN/OUT ports:
 *
 * - Any read where A0=0 is the ULA. The low 5 bits return the keyboard matrix for the rows
 *   selected by port high byte (bit=0 = row selected). Bit 6 returns 1 (EAR idle; M3 may
 *   override for tape input). Bit 5 returns 1 (bus idle high). Bit 7 returns 1.
 * - Any write where A0=0 is the ULA. The low 3 bits are border color (M2.8); bit 4 is the
 *   beeper bit (M2.6). M2.5 stubs the write as a no-op so the CPU doesn't crash.
 * - Non-ULA ports (A0=1) read 0xFF and ignore writes (M1 NoIoBus behavior preserved).
 */
class SpectrumIoBus(private val keyboard: Keyboard) : IoBus {
    override fun read(port: Int): Int =
        if ((port and 0x01) == 0) {
            val matrix = keyboard.read((port ushr 8) and 0xFF)
            // Bit 5 idle, bit 6 EAR=1, bit 7 (open) = 1. So OR 0xA0 onto the 5-bit matrix.
            matrix or 0xA0
        } else 0xFF

    override fun write(port: Int, value: Int) {
        // ULA writes (border / beeper) land here; M2.8 and M2.6 will fill in. Today: drop.
    }
}
```

### M2.5-D: HostKeyMap + SpectrumWindow KeyListener

New `ui/HostKeyMap.kt`:

```kotlin
package ru.alepar.zx80.ui

import java.awt.event.KeyEvent
import ru.alepar.zx80.machine.SpectrumKey

/**
 * Maps an AWT KeyEvent.VK_* code to the SpectrumKey(s) it should press. Returns an empty list
 * for unmapped keys.
 *
 * The mapping covers letters, digits, ENTER, SPACE, the two SHIFTs, and the canonical Spectrum
 * aliases for arrows (CAPS+5/6/7/8) and DELETE (CAPS+0). Symbol keys (`;`, `:`, `[`, etc.) are
 * not mapped — typing punctuation on a Spectrum requires SYMBOL SHIFT + a specific key; users
 * who want that should hold Ctrl + the appropriate key directly.
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

Modify `SpectrumWindow`:
- Constructor gains a `keyboard: Keyboard` parameter.
- `show()` installs a `KeyListener` on `panel` (and makes the panel focusable so it gets key events). Maintains a `currentlyDown: MutableSet<Int>` to ignore repeated `keyPressed` for the same code (Java fires repeats for held keys).
- `windowDeactivated` and `focusLost`: `keyboard.releaseAll()` and `currentlyDown.clear()` so stuck keys are cleared when focus leaves the window.

### M2.5-E: SpectrumCommand wiring

Modify `SpectrumCommand.run()` to construct and wire the keyboard chain:

```kotlin
override fun run() {
    val machine = Spectrum48k()
    val keyboard = Keyboard()
    machine.cpu.io = SpectrumIoBus(keyboard)
    machine.reset()
    val pacer = Pacer(machine, UlaRenderer())
    val window = SpectrumWindow(pacer, keyboard, scale)
    window.show()
}
```

The bus must be installed BEFORE `reset()` for IN behavior to match real
hardware on the boot sequence, though in practice `reset()` itself doesn't
do any I/O — the order is forward-compatible insurance.

### Out of scope

- Symbol keys (`;`, `:`, `[`, etc.) — see Q1 answer in brainstorming
- Joystick / Kempston interface (M3 likely)
- EAR/MIC tape input (M3)
- Key repeat handling (real Spectrum doesn't have HW autorepeat; ROM handles it from the 50Hz INT)
- Caps Lock toggle behavior (use Shift instead)
- Beeper writes (M2.6)
- Border color writes (M2.8)

## Architecture

```
src/main/kotlin/ru/alepar/zx80/
  machine/SpectrumKey.kt       NEW
  machine/Keyboard.kt          NEW
  machine/SpectrumIoBus.kt     NEW
  ui/HostKeyMap.kt             NEW
  ui/SpectrumWindow.kt         MODIFY (constructor + KeyListener + releaseAll on focus loss)
  cli/SpectrumCommand.kt       MODIFY (construct + wire Keyboard / SpectrumIoBus)
src/test/kotlin/ru/alepar/zx80/
  machine/SpectrumKeyTest.kt   NEW
  machine/KeyboardTest.kt      NEW
  machine/SpectrumIoBusTest.kt NEW
  ui/HostKeyMapTest.kt         NEW
```

**Threading model.** `Keyboard.press/release/releaseAll` are called only
from the EDT (Swing KeyListener callbacks). `Keyboard.read` is called
only from the Pacer thread (via `SpectrumIoBus.read` during
`machine.runFrame()`). The cross-thread channel is the
`AtomicIntegerArray` of rows. `pressCounts` is EDT-local; the
`getAndUpdate` of `rows` happens-before any subsequent reader-thread
`get` (release/acquire semantics of the AtomicIntegerArray).

**No Spectrum48k changes.** The CPU reads `cpu.io` for every IN/OUT; we
just swap the io field. `Spectrum48k`'s public surface is untouched.

## Test strategy

### SpectrumKeyTest — 4 assertions

```kotlin
@Test fun `CAPS_SHIFT is at row 0 bit 0`() { ... }
@Test fun `SPACE is at row 7 bit 0`() { ... }
@Test fun `B is at row 7 bit 4`() { ... }
@Test fun `there are exactly 40 keys`() {
    assertThat(SpectrumKey.values()).hasSize(40)
}
```

### KeyboardTest — 9 assertions

```kotlin
@Test fun `fresh keyboard reads 0x1F across all rows`() {
    val kb = Keyboard()
    assertThat(kb.read(0x00)).isEqualTo(0x1F)
}

@Test fun `press A clears row 1 bit 0`() {
    val kb = Keyboard()
    kb.press(SpectrumKey.A)
    assertThat(kb.read(0xFD)).isEqualTo(0x1E)  // row 1 selected; bit 0 clear
}

@Test fun `release A restores row to 0x1F`() {
    val kb = Keyboard()
    kb.press(SpectrumKey.A)
    kb.release(SpectrumKey.A)
    assertThat(kb.read(0xFD)).isEqualTo(0x1F)
}

@Test fun `refcount keeps key pressed across one early release`() {
    val kb = Keyboard()
    kb.press(SpectrumKey.A); kb.press(SpectrumKey.A); kb.release(SpectrumKey.A)
    assertThat(kb.read(0xFD)).isEqualTo(0x1E)  // still pressed
}

@Test fun `refcount releases key after all presses unwound`() {
    val kb = Keyboard()
    kb.press(SpectrumKey.A); kb.press(SpectrumKey.A)
    kb.release(SpectrumKey.A); kb.release(SpectrumKey.A)
    assertThat(kb.read(0xFD)).isEqualTo(0x1F)
}

@Test fun `read 0xFE selects only row 0`() {
    val kb = Keyboard()
    kb.press(SpectrumKey.CAPS_SHIFT)  // row 0 bit 0
    kb.press(SpectrumKey.A)           // row 1 bit 0
    assertThat(kb.read(0xFE)).isEqualTo(0x1E)  // only row 0; bit 0 clear
}

@Test fun `read 0xFC ANDs rows 0 and 1`() {
    val kb = Keyboard()
    kb.press(SpectrumKey.CAPS_SHIFT)  // row 0 bit 0
    assertThat(kb.read(0xFC)).isEqualTo(0x1E)  // AND(row 0 = 0x1E, row 1 = 0x1F) = 0x1E
}

@Test fun `read 0xFF selects no rows and returns 0x1F`() {
    val kb = Keyboard()
    kb.press(SpectrumKey.A)
    assertThat(kb.read(0xFF)).isEqualTo(0x1F)  // no rows selected; result stays 0xFF then & 0x1F
}

@Test fun `release of never-pressed key is a no-op`() {
    val kb = Keyboard()
    kb.release(SpectrumKey.Z)
    assertThat(kb.read(0xFE)).isEqualTo(0x1F)
}

@Test fun `releaseAll resets all rows and refcounts`() {
    val kb = Keyboard()
    kb.press(SpectrumKey.A); kb.press(SpectrumKey.A); kb.press(SpectrumKey.B)
    kb.releaseAll()
    assertThat(kb.read(0x00)).isEqualTo(0x1F)
    // After releaseAll, a single release of A must NOT decrement past zero.
    kb.release(SpectrumKey.A)
    assertThat(kb.read(0xFD)).isEqualTo(0x1F)
}
```

Adjusted total: 10 assertions.

### SpectrumIoBusTest — 5 assertions

```kotlin
@Test fun `read ULA port 0xFEFE returns row 0 ORed with 0xA0`() {
    val kb = Keyboard().apply { press(SpectrumKey.CAPS_SHIFT) }
    val bus = SpectrumIoBus(kb)
    assertThat(bus.read(0xFEFE)).isEqualTo(0xA0 or 0x1E)
}

@Test fun `read ULA port 0xFDFE returns row 1 ORed with 0xA0`() {
    // Port 0xFDFE: low byte 0xFE has A0=0 (ULA); high byte 0xFD = 0b11111101 selects row 1.
    val kb = Keyboard().apply { press(SpectrumKey.A) }
    val bus = SpectrumIoBus(kb)
    assertThat(bus.read(0xFDFE)).isEqualTo(0xA0 or 0x1E)
}

@Test fun `read ULA port 0xFFFE returns no-row 0x1F ORed with 0xA0`() {
    val bus = SpectrumIoBus(Keyboard())
    assertThat(bus.read(0xFFFE)).isEqualTo(0xBF)  // 0xA0 | 0x1F
}

@Test fun `read non-ULA port returns 0xFF`() {
    val bus = SpectrumIoBus(Keyboard())
    assertThat(bus.read(0xFEFF)).isEqualTo(0xFF)  // A0=1; not ULA
}

@Test fun `write does not throw`() {
    val bus = SpectrumIoBus(Keyboard())
    bus.write(0xFEFE, 0x07)  // border color attempt; stubbed as no-op for M2.5
}
```

### HostKeyMapTest — 7 assertions

```kotlin
@Test fun `VK_A maps to A`() { assertThat(HostKeyMap.map(KeyEvent.VK_A)).containsExactly(SpectrumKey.A) }
@Test fun `VK_ENTER maps to ENTER`() { ... }
@Test fun `VK_SHIFT maps to CAPS_SHIFT`() { ... }
@Test fun `VK_CONTROL maps to SYMBOL_SHIFT`() { ... }
@Test fun `VK_BACK_SPACE maps to CAPS_SHIFT+K0`() {
    assertThat(HostKeyMap.map(KeyEvent.VK_BACK_SPACE))
        .containsExactly(SpectrumKey.CAPS_SHIFT, SpectrumKey.K0)
}
@Test fun `VK_LEFT maps to CAPS_SHIFT+K5`() { ... }
@Test fun `VK_F12 is unmapped`() { assertThat(HostKeyMap.map(KeyEvent.VK_F12)).isEmpty() }
```

**Total: ~26 new assertions across 4 new test files.**

## Validation gates (WU M2.5-F)

1. `./gradlew clean check installDist` — BUILD SUCCESSFUL.
2. All new tests pass (~26 assertions).
3. Existing tests still pass.
4. ZEXDOC clean (0 IllegalStateException, 0 ERROR).
5. FUSE: 1354/1356.
6. Programs: 5/5.
7. Composite SCORE: ≥0.966.
8. **Headless CLI gate:** `./build/install/zx80/bin/zx80 spectrum --help` still prints usage and exits 0. (Defends against accidental CLI breakage.)
9. **Manual smoke (DISPLAY required, deferred to desktop):** launch
   `zx80 spectrum`, press a few keys (e.g. SPACE), verify no crash and
   the window remains responsive. ROM is still stuck (per `zx80-1qc`),
   so we can't observe keypresses reaching BASIC. After Phase H lands,
   re-run and verify typed text appears.

Tag: `m2-phase01-5`.

## Work-unit breakdown

| WU | Description |
|---|---|
| M2.5-A | `SpectrumKey` enum + `SpectrumKeyTest`. |
| M2.5-B | `Keyboard` class with AtomicIntegerArray + pressCounts refcount + releaseAll + `KeyboardTest`. |
| M2.5-C | `SpectrumIoBus` class + `SpectrumIoBusTest`. |
| M2.5-D | `HostKeyMap` object + `HostKeyMapTest`. `SpectrumWindow` constructor gains `keyboard` parameter; install KeyListener on panel with `currentlyDown` Set to dedupe held-key repeats; on `windowDeactivated` and `focusLost` call `keyboard.releaseAll()` and clear `currentlyDown`. Panel must be focusable. |
| M2.5-E | `SpectrumCommand` constructs `Keyboard`, sets `machine.cpu.io = SpectrumIoBus(keyboard)` before `reset()`, passes keyboard to SpectrumWindow. |
| M2.5-F | Sweep + tag `m2-phase01-5` + manual smoke. |

Within-phase deps: A → B (Keyboard uses SpectrumKey). C depends on B
(bus reads Keyboard). D depends on B (HostKeyMap returns SpectrumKey;
KeyListener calls Keyboard.press/release). E depends on B+C+D. F
depends on all. Linear order recommended.

## Risks

- **Focus-lost stuck keys.** If the window loses focus while a key is
  held, `keyReleased` won't fire and the Spectrum thinks the key is
  still pressed. Mitigated by `releaseAll` on `windowDeactivated` and
  `focusLost`. Add this in M2.5-D.
- **Java key repeat double-press.** On some platforms, holding a key
  fires repeated `keyPressed` without intervening `keyReleased`,
  driving the refcount up. Mitigated by `currentlyDown: MutableSet<Int>`
  guard in the listener — ignore `keyPressed` for codes already in the
  set; remove on `keyReleased`.
- **Bit/row math.** ULA port high byte: bit=0 means row IS selected
  (active-low). Easy to invert. Tested explicitly.
- **CapsLock confusion.** A user might press CapsLock expecting CAPS
  SHIFT. CapsLock is a toggle, not a held-modifier; we don't map it.
  The CLI doesn't document this yet — punt to a README in a later WU.
- **EDT-only assumption for pressCounts.** Documented in the Keyboard
  kdoc. If we ever need to call press/release from a non-EDT thread
  (e.g. scripted input for tests), we'd need to synchronize
  `pressCounts` too. Tests call press/release directly on the test
  thread, which is fine because no other thread is touching pressCounts
  during a test. Tests' direct access works because JUnit runs each
  test single-threadedly.
- **Spectrum48k.cpu.io install order.** Setting cpu.io BEFORE reset()
  is forward-compatible insurance. M1's reset doesn't touch I/O; M2.2's
  doesn't either. If a future reset path does, the bus is already
  installed.
