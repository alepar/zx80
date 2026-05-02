package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand

/** Stub: future home of "disassemble a Z80 binary" wiring. */
class DisasmCommand : CliktCommand(name = "disasm") {
    override fun run() {
        echo("disasm: not yet implemented (will disassemble a Z80 binary)", err = true)
    }
}
