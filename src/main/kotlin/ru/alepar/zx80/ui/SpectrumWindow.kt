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
