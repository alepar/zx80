package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import ru.alepar.zx80.zexdoc.ZexdocRunner

/** Runs the bundled ZEXDOC.COM Z80 documented-instruction conformance suite. */
class ZexdocCommand : CliktCommand(name = "zexdoc") {
    private val maxCycles by
        option("--max-cycles", help = "Cycle budget; 0 means unlimited (full run)")
            .long()
            .default(0L)

    private val quiet by
        option("--quiet", help = "Suppress live output streaming; only emit final summary").flag()

    override fun run() {
        val rom =
            ZexdocCommand::class.java.getResourceAsStream("/zexdoc/ZEXDOC.COM")
                ?: error("ZEXDOC.COM not bundled; expected at /zexdoc/ZEXDOC.COM on classpath")

        val sink: Appendable = if (quiet) StringBuilder() else SystemOutAppendable()
        val runner = ZexdocRunner(sink)
        val result =
            runner.run(
                rom.use { it.readBytes() },
                maxCycles = if (maxCycles == 0L) Long.MAX_VALUE else maxCycles,
            )

        if (quiet) echo(sink.toString())

        echo("--- ZEXDOC run summary ---", err = true)
        echo("cycles=${result.cycles}", err = true)
        echo("halted=${result.halted}", err = true)

        val hasError = result.output.contains("ERROR", ignoreCase = false)
        val complete = result.output.contains("Tests complete", ignoreCase = false)
        if (hasError || !complete) {
            echo("FAIL: errors=$hasError complete=$complete", err = true)
            throw com.github.ajalt.clikt.core.ProgramResult(1)
        }
        echo("PASS: all tests OK", err = true)
    }

    /**
     * Streams chars to `System.out` as ZEXDOC emits them AND captures them into a buffer so the
     * post-run `result.output.contains("Tests complete")` check actually has text to read.
     */
    internal class SystemOutAppendable : Appendable {
        private val captured = StringBuilder()

        override fun append(c: Char): Appendable {
            print(c)
            System.out.flush()
            captured.append(c)
            return this
        }

        override fun append(csq: CharSequence?): Appendable {
            val s = csq ?: "null"
            print(s)
            captured.append(s)
            return this
        }

        override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
            val s = (csq ?: "null").subSequence(start, end)
            print(s)
            captured.append(s)
            return this
        }

        override fun toString(): String = captured.toString()
    }
}
