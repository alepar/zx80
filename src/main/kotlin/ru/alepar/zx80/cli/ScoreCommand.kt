package ru.alepar.zx80.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import ru.alepar.zx80.harness.Score
import ru.alepar.zx80.harness.suites.BootsToBasic
import ru.alepar.zx80.harness.suites.FuseSuite
import ru.alepar.zx80.harness.suites.GameMilestoneSuite
import ru.alepar.zx80.harness.suites.OpcodeCoverage
import ru.alepar.zx80.harness.suites.ProgramsSuite
import ru.alepar.zx80.harness.suites.TapeParserSuite
import ru.alepar.zx80.harness.suites.TapePulseLoadSuite
import ru.alepar.zx80.harness.suites.TapeTrapLoadSuite
import ru.alepar.zx80.op.OpTableBuilder

/**
 * `zx80 score` — runs the three scoring suites against an empty Decoder, prints the headline, and
 * writes `build/score.json` (rotating any prior file to `build/score.prev.json`).
 *
 * The `--suite=<name>` filter narrows execution to a single suite; `--strict` is reserved for
 * regression-vs-prev comparisons once Phase 2 starts producing nonzero scores.
 */
class ScoreCommand : CliktCommand(name = "score") {
    private val suiteFilter by option("--suite", help = "run only this suite").default("all")
    private val strict by
        option("--strict", help = "exit nonzero on regression (currently a no-op)").flag()

    override fun run() {
        val decoder = OpTableBuilder.build()
        val all =
            listOf(
                OpcodeCoverage(decoder),
                FuseSuite(
                    decoder,
                    ResourceLoader.loadFuseInputs(),
                    ResourceLoader.loadFuseExpected(),
                ),
                ProgramsSuite(decoder, ResourceLoader.loadPrograms()),
                BootsToBasic(decoder),
                TapeParserSuite(),
                TapeTrapLoadSuite(),
                TapePulseLoadSuite(),
                GameMilestoneSuite(),
            )
        val selected = if (suiteFilter == "all") all else all.filter { it.name == suiteFilter }
        if (selected.isEmpty()) throw CliktError("unknown suite: $suiteFilter")

        val composite = Score.compute(selected)

        // Print headline FIRST so a subsequent JSON-write failure doesn't swallow the result.
        echo(composite.headline())

        val out = Path.of("build/score.json")
        val prev = Path.of("build/score.prev.json")
        Files.createDirectories(out.parent)
        if (Files.exists(out)) Files.move(out, prev, StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(out, composite.toJson(prettyPrint = true))

        // --strict regression handling deferred until we have a baseline
        // (not meaningful when the score is always 0). Accepted so we don't
        // have to re-thread the option through the CLI later.
        if (strict) {
            // Placeholder — will compare against build/score.prev.json once
            // Phase 2 starts producing nonzero scores.
        }
    }
}
