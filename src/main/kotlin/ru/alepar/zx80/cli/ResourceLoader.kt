package ru.alepar.zx80.cli

import kotlinx.serialization.json.Json
import ru.alepar.zx80.harness.fuse.FuseTestParser
import ru.alepar.zx80.harness.programs.ProgramExpectation
import ru.alepar.zx80.harness.suites.ProgramFixture

/**
 * Loads vendored test data (FUSE inputs/expected, program fixtures) from the classpath. Backs the
 * `score` subcommand; isolated here so non-CLI callers (tests, future tools) can reuse the same
 * loaders without dragging in Clikt.
 */
object ResourceLoader {

    fun loadFuseInputs() = FuseTestParser.parseInputs(classpathLines("/fuse/tests.in"))

    fun loadFuseExpected() = FuseTestParser.parseExpected(classpathLines("/fuse/tests.expected"))

    fun loadPrograms(): List<ProgramFixture> =
        PROGRAM_NAMES.map { name ->
            val bytes = readClasspathBytes("/programs/$name.bin")
            val json = readClasspathText("/programs/$name.expected.json")
            // NOTE: deliberately NOT using ignoreUnknownKeys — partial expectations
            // belong in nullable values, not unknown keys. A typo like `pcc:` should
            // be a parse error, not silently dropped.
            val exp = Json.decodeFromString(ProgramExpectation.serializer(), json)
            ProgramFixture(bytes = bytes, expectation = exp)
        }

    private val PROGRAM_NAMES = listOf("nop_loop", "fib10", "memcpy16")

    private fun classpathLines(path: String): Sequence<String> {
        // Eagerly read all lines, then expose as a sequence: the underlying stream
        // must be closed before the sequence is consumed by the parser, so a lazy
        // `useLines` would yield an already-closed reader.
        val stream =
            ResourceLoader::class.java.getResourceAsStream(path)
                ?: error("classpath resource not found: $path")
        return stream.bufferedReader().use { it.readLines() }.asSequence()
    }

    private fun readClasspathBytes(path: String): ByteArray =
        ResourceLoader::class.java.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("classpath resource not found: $path")

    private fun readClasspathText(path: String): String =
        ResourceLoader::class.java.getResourceAsStream(path)?.use { it.bufferedReader().readText() }
            ?: error("classpath resource not found: $path")
}
