package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

/** Root command — pure dispatcher; the work lives in the subcommands. */
class Zx80Cli : CliktCommand(name = "zx80") {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    Zx80Cli()
        .subcommands(
            ScoreCommand(),
            RunCommand(),
            DisasmCommand(),
            BenchCommand(),
            ZexdocCommand(),
            SpectrumCommand(),
        )
        .main(args)
}
