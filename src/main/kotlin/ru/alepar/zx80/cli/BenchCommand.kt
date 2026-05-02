package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand

/** Stub: future home of "benchmark Z80 execution throughput" wiring. */
class BenchCommand : CliktCommand(name = "bench") {
    override fun run() {
        echo("bench: not yet implemented (will benchmark Z80 execution throughput)", err = true)
    }
}
