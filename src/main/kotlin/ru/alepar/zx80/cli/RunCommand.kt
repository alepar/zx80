package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand

/** Stub: future home of "load and execute a Z80 binary" wiring. */
class RunCommand : CliktCommand(name = "run") {
    override fun run() {
        echo("run: not yet implemented (will load a Z80 binary and execute it)", err = true)
    }
}
