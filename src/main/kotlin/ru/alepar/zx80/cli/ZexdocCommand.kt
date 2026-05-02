package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand

/** Stub: future home of "run the ZEXDOC documented-instruction conformance suite" wiring. */
class ZexdocCommand : CliktCommand(name = "zexdoc") {
    override fun run() {
        echo("zexdoc: not yet implemented (will run the ZEXDOC conformance suite)", err = true)
    }
}
