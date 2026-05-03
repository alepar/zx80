package ru.alepar.zx80.harness.fuse

/**
 * Initial state for one FUSE test case (from `tests.in`).
 *
 * The FUSE input format per test (delimited by blank lines):
 * 1. test name
 * 2. 13 hex tokens — `AF BC DE HL AF' BC' DE' HL' IX IY SP PC MEMPTR`
 * 3. 7 tokens — `I R IFF1 IFF2 IM HALTED TSTATES`
 * 4. zero or more memory blocks `addr byte... -1`
 * 5. standalone `-1` line terminating the list of memory blocks
 */
data class FuseInputCase(
    val name: String,
    val af: Int,
    val bc: Int,
    val de: Int,
    val hl: Int,
    val afAlt: Int,
    val bcAlt: Int,
    val deAlt: Int,
    val hlAlt: Int,
    val ix: Int,
    val iy: Int,
    val sp: Int,
    val pc: Int,
    val memptr: Int,
    val i: Int,
    val r: Int,
    val iff1: Boolean,
    val iff2: Boolean,
    val im: Int,
    val halted: Boolean,
    val tStatesToRun: Int,
    /** List of `(start_addr, bytes)` blocks. */
    val memory: List<Pair<Int, ByteArray>>,
)

/**
 * A `PR` (port read) event from a FUSE expected file. Ordered by T-state offset within the test;
 * the FUSE harness will pop these in order from a `QueueIoBus` to satisfy `IN`/`INI` etc.
 */
data class PortRead(val port: Int, val byte: Int)

/**
 * Expected post-instruction state (from `tests.expected`).
 *
 * Same shape as the input case, except that:
 * - The state line is preceded by zero or more event lines (lines starting with whitespace). PR
 *   (port read) events are captured into [portReads]; all other event types (MC, MR, MW, PW, ...)
 *   are ignored.
 * - The memory section has zero or one block `addr byte... -1` and is NOT followed by a standalone
 *   `-1` terminator — a blank line (or EOF) marks end of the test.
 */
data class FuseExpectedCase(
    val name: String,
    val af: Int,
    val bc: Int,
    val de: Int,
    val hl: Int,
    val afAlt: Int,
    val bcAlt: Int,
    val deAlt: Int,
    val hlAlt: Int,
    val ix: Int,
    val iy: Int,
    val sp: Int,
    val pc: Int,
    val memptr: Int,
    val i: Int,
    val r: Int,
    val iff1: Boolean,
    val iff2: Boolean,
    val im: Int,
    val halted: Boolean,
    val tStatesAfter: Int,
    val memory: List<Pair<Int, ByteArray>>,
    val portReads: List<PortRead> = emptyList(),
)

object FuseTestParser {

    private const val STATE_TOKEN_COUNT = 13
    private const val CONTROL_TOKEN_COUNT = 7

    fun parseInputs(lines: Sequence<String>): List<FuseInputCase> {
        val it = lines.iterator()
        val result = mutableListOf<FuseInputCase>()
        while (true) {
            val name = it.nextNonBlankOrNull() ?: break
            val regs =
                it.nextRequired("state line for '$name'").splitTokens(STATE_TOKEN_COUNT, name)
            val ctrl =
                it.nextRequired("control line for '$name'").splitTokens(CONTROL_TOKEN_COUNT, name)
            val mem = readInputMemory(it, name)
            result.add(
                FuseInputCase(
                    name = name,
                    af = regs[0].toInt(16),
                    bc = regs[1].toInt(16),
                    de = regs[2].toInt(16),
                    hl = regs[3].toInt(16),
                    afAlt = regs[4].toInt(16),
                    bcAlt = regs[5].toInt(16),
                    deAlt = regs[6].toInt(16),
                    hlAlt = regs[7].toInt(16),
                    ix = regs[8].toInt(16),
                    iy = regs[9].toInt(16),
                    sp = regs[10].toInt(16),
                    pc = regs[11].toInt(16),
                    memptr = regs[12].toInt(16),
                    i = ctrl[0].toInt(16),
                    r = ctrl[1].toInt(16),
                    iff1 = ctrl[2].toBoolFlexible(),
                    iff2 = ctrl[3].toBoolFlexible(),
                    im = ctrl[4].toInt(),
                    halted = ctrl[5] == "1",
                    tStatesToRun = ctrl[6].toInt(),
                    memory = mem,
                )
            )
        }
        return result
    }

    fun parseExpected(lines: Sequence<String>): List<FuseExpectedCase> {
        val it = lines.iterator()
        val result = mutableListOf<FuseExpectedCase>()
        while (true) {
            val name = it.nextNonBlankOrNull() ?: break
            val portReads = mutableListOf<PortRead>()
            val stateLine =
                it.collectEventsAndNext(portReads)
                    ?: error("missing state line for '$name' in tests.expected")
            val regs = stateLine.splitTokens(STATE_TOKEN_COUNT, name)
            val ctrl =
                it.nextRequired("control line for '$name'").splitTokens(CONTROL_TOKEN_COUNT, name)
            val mem = readExpectedMemory(it, name)
            result.add(
                FuseExpectedCase(
                    name = name,
                    af = regs[0].toInt(16),
                    bc = regs[1].toInt(16),
                    de = regs[2].toInt(16),
                    hl = regs[3].toInt(16),
                    afAlt = regs[4].toInt(16),
                    bcAlt = regs[5].toInt(16),
                    deAlt = regs[6].toInt(16),
                    hlAlt = regs[7].toInt(16),
                    ix = regs[8].toInt(16),
                    iy = regs[9].toInt(16),
                    sp = regs[10].toInt(16),
                    pc = regs[11].toInt(16),
                    memptr = regs[12].toInt(16),
                    i = ctrl[0].toInt(16),
                    r = ctrl[1].toInt(16),
                    iff1 = ctrl[2].toBoolFlexible(),
                    iff2 = ctrl[3].toBoolFlexible(),
                    im = ctrl[4].toInt(),
                    halted = ctrl[5] == "1",
                    tStatesAfter = ctrl[6].toInt(),
                    memory = mem,
                    portReads = portReads.toList(),
                )
            )
        }
        return result
    }

    /** Read tests.in memory blocks until a standalone `-1` line. */
    private fun readInputMemory(it: Iterator<String>, name: String): List<Pair<Int, ByteArray>> {
        val blocks = mutableListOf<Pair<Int, ByteArray>>()
        while (it.hasNext()) {
            val line = it.next().trim()
            if (line.isEmpty()) continue
            if (line == "-1") return blocks
            blocks.add(parseMemoryBlock(line, name))
        }
        error("unterminated memory list for '$name' in tests.in (expected '-1' terminator)")
    }

    /**
     * Read tests.expected memory blocks until a blank line or EOF. Each line is one block ending
     * with `-1`; there is no overall list terminator.
     */
    private fun readExpectedMemory(it: Iterator<String>, name: String): List<Pair<Int, ByteArray>> {
        val blocks = mutableListOf<Pair<Int, ByteArray>>()
        while (it.hasNext()) {
            val raw = it.next()
            if (raw.isBlank()) return blocks
            blocks.add(parseMemoryBlock(raw.trim(), name))
        }
        return blocks
    }

    private fun parseMemoryBlock(line: String, name: String): Pair<Int, ByteArray> {
        val toks = line.split(Regex("\\s+"))
        require(toks.size >= 2) { "malformed memory block for '$name': '$line'" }
        require(toks.last() == "-1") { "memory block missing -1 terminator for '$name': '$line'" }
        val addr = toks[0].toInt(16)
        val bytes =
            toks.drop(1).takeWhile { it != "-1" }.map { it.toInt(16).toByte() }.toByteArray()
        return addr to bytes
    }

    private fun String.splitTokens(expected: Int, name: String): List<String> {
        val toks = trim().split(Regex("\\s+"))
        require(toks.size == expected) {
            "expected $expected tokens for '$name', got ${toks.size}: '$this'"
        }
        return toks
    }

    private fun Iterator<String>.nextRequired(what: String): String {
        require(hasNext()) { "missing $what" }
        return next()
    }

    /**
     * Walk event lines (start with whitespace) capturing any `PR` (port read) events into [sink],
     * and return the next non-event, non-blank line (the state line). Other event types (MC, MR,
     * MW, PW, etc.) are ignored. Format of a PR line is `<tstate> PR <port> <byte>` after trim;
     * port and byte are hex.
     */
    private fun Iterator<String>.collectEventsAndNext(sink: MutableList<PortRead>): String? {
        while (hasNext()) {
            val line = next()
            if (line.isBlank()) continue
            if (line.startsWith(' ') || line.startsWith('\t')) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4 && parts[1] == "PR") {
                    sink.add(PortRead(port = parts[2].toInt(16), byte = parts[3].toInt(16)))
                }
                continue
            }
            return line
        }
        return null
    }

    private fun Iterator<String>.nextNonBlankOrNull(): String? {
        while (hasNext()) {
            val s = next()
            if (s.isNotBlank()) return s.trim()
        }
        return null
    }

    private fun String.toBoolFlexible(): Boolean = this == "1" || equals("true", ignoreCase = true)
}
