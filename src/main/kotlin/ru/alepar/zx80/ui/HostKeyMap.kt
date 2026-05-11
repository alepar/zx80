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
