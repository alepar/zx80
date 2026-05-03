package ru.alepar.zx80.harness.io

import ru.alepar.zx80.cpu.IoBus
import ru.alepar.zx80.harness.fuse.PortRead

/**
 * Drives `cpu.io` from a pre-recorded list of `PR` (port read) events extracted from a FUSE
 * expected case. Reads pop the queue head and return its byte value; once exhausted, returns 0xFF
 * (matching the project's default `NoIoBus`). Writes are dropped — FUSE does not currently validate
 * port writes (`PW` events).
 *
 * The queue is consumed strictly in order, ignoring the requested port: FUSE's PR events list the
 * port for documentation but in practice the Z80 always reads the port specified by the IN op, so
 * pop-and-return is sufficient.
 */
class QueueIoBus(reads: List<PortRead>) : IoBus {
    private val pending = ArrayDeque(reads)

    override fun read(port: Int): Int {
        val next = pending.removeFirstOrNull() ?: return 0xFF
        return next.byte and 0xFF
    }

    override fun write(port: Int, value: Int) {
        // No-op for FUSE; PW events not validated.
    }
}
