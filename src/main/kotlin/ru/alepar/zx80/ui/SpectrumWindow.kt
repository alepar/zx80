package ru.alepar.zx80.ui

import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Swing-based host window that displays the Pacer's framebuffer. Opens a non-resizable JFrame
 * sized 256*scale x 192*scale. Spawns a daemon thread that loops Pacer.stepOneFrame and
 * schedules repaints on the EDT.
 *
 * On window close: signals the pacer thread to stop, waits up to 500ms, disposes the frame,
 * and calls exitProcess(0). Standard emulator shutdown behavior.
 */
class SpectrumWindow(private val pacer: Pacer, private val scale: Int = 2) {
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

    fun show() {
        panel.preferredSize = Dimension(256 * scale, 192 * scale)
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
    }
}
