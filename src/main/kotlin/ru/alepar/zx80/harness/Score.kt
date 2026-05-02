package ru.alepar.zx80.harness

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.alepar.zx80.harness.suites.Suite

/**
 * Computes a weight-normalised composite from a list of suite results, formats the stdout headline
 * line, and serialises the full breakdown to JSON for the autonomous-loop diff.
 */
data class CompositeScore(val score: Double, val results: List<SuiteResult>) {
    fun headline(): String {
        val s = "%.3f".format(java.util.Locale.US, score)
        val parts = results.joinToString(", ") { "${it.name} ${it.passed}/${it.total}" }
        return "SCORE: $s  ($parts)"
    }

    fun toJson(prettyPrint: Boolean = true, gitInfo: GitInfo = GitInfo.unknown()): String {
        // Shape contract: docs/superpowers/specs/2026-05-02-zx80-spectrum-emulator-design.md
        // §"build/score.json"
        val obj = buildJsonObject {
            put("score", JsonPrimitive(score))
            put("timestamp", JsonPrimitive(Instant.now().toString()))
            put(
                "git",
                buildJsonObject {
                    put("branch", JsonPrimitive(gitInfo.branch))
                    put("sha", JsonPrimitive(gitInfo.sha))
                    put("dirty", JsonPrimitive(gitInfo.dirty))
                },
            )
            put(
                "suites",
                buildJsonObject {
                    for (r in results) {
                        put(
                            r.name,
                            buildJsonObject {
                                put("weight", JsonPrimitive(r.weight))
                                put("passed", JsonPrimitive(r.passed))
                                put("total", JsonPrimitive(r.total))
                                put("ratio", JsonPrimitive(r.ratio))
                                put("details", r.details)
                            },
                        )
                    }
                },
            )
        }
        val codec = if (prettyPrint) PRETTY else COMPACT
        return codec.encodeToString(JsonElement.serializer(), obj)
    }

    companion object {
        private val PRETTY = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        private val COMPACT = Json {
            prettyPrint = false
            encodeDefaults = true
        }
    }
}

data class GitInfo(val branch: String, val sha: String, val dirty: Boolean) {
    companion object {
        fun unknown() = GitInfo("?", "?", false)
    }
}

object Score {
    fun compute(suites: List<Suite>): CompositeScore {
        val results = suites.map { it.run() }
        val score = results.sumOf { it.weight * it.ratio }
        return CompositeScore(score = score, results = results)
    }
}
