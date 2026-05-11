package ru.alepar.zx80.integration

import ru.alepar.zx80.machine.Keyboard
import ru.alepar.zx80.machine.Spectrum48k
import ru.alepar.zx80.machine.SpectrumKey

/**
 * Simulates a human typing on the Spectrum keyboard. Each press holds the key for [framesHeld]
 * frames (so the ROM's 50Hz key-scan ISR samples it), releases, then idles for [framesReleased]
 * before the next press (so the ROM's debounce treats the next press as new).
 *
 * Test-only utility; lives under `src/test/`.
 */
class SpectrumTypist(
    private val machine: Spectrum48k,
    private val keyboard: Keyboard,
    private val framesHeld: Int = 5,
    private val framesReleased: Int = 5,
) {
    /** Press and release a single key. */
    fun press(key: SpectrumKey) {
        keyboard.press(key)
        repeat(framesHeld) { machine.runFrame() }
        keyboard.release(key)
        repeat(framesReleased) { machine.runFrame() }
    }

    /** Press a key while a modifier (CAPS_SHIFT or SYMBOL_SHIFT) is held. */
    fun withMod(modifier: SpectrumKey, key: SpectrumKey) {
        keyboard.press(modifier)
        keyboard.press(key)
        repeat(framesHeld) { machine.runFrame() }
        keyboard.release(key)
        keyboard.release(modifier)
        repeat(framesReleased) { machine.runFrame() }
    }

    fun enter() = press(SpectrumKey.ENTER)
    fun space() = press(SpectrumKey.SPACE)
}
