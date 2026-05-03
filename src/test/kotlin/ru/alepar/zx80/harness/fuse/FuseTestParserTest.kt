package ru.alepar.zx80.harness.fuse

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FuseTestParserTest {
    @Test
    fun `parses a single input test case`() {
        // Real FUSE tests.in format:
        //   line 1: name
        //   line 2: 13 hex tokens — AF BC DE HL AF' BC' DE' HL' IX IY SP PC MEMPTR
        //   line 3: 7 tokens — I R IFF1 IFF2 IM HALTED TSTATES
        //   memory blocks: "addr byte... -1"
        //   standalone "-1" terminates the list of memory blocks
        val src =
            """
            00
            1234 5678 9abc def0 0011 2233 4455 6677 8899 aabb ccdd eeff 0000
            12 34 1 0 2 0 4
            0100 00 -1
            -1
            """
                .trimIndent()
        val tests = FuseTestParser.parseInputs(src.lineSequence())
        assertThat(tests).hasSize(1)
        val t = tests.single()
        assertThat(t.name).isEqualTo("00")
        assertThat(t.af).isEqualTo(0x1234)
        assertThat(t.bc).isEqualTo(0x5678)
        assertThat(t.de).isEqualTo(0x9ABC)
        assertThat(t.hl).isEqualTo(0xDEF0)
        assertThat(t.afAlt).isEqualTo(0x0011)
        assertThat(t.bcAlt).isEqualTo(0x2233)
        assertThat(t.deAlt).isEqualTo(0x4455)
        assertThat(t.hlAlt).isEqualTo(0x6677)
        assertThat(t.ix).isEqualTo(0x8899)
        assertThat(t.iy).isEqualTo(0xAABB)
        assertThat(t.sp).isEqualTo(0xCCDD)
        assertThat(t.pc).isEqualTo(0xEEFF)
        assertThat(t.memptr).isEqualTo(0x0000)
        assertThat(t.i).isEqualTo(0x12)
        assertThat(t.r).isEqualTo(0x34)
        assertThat(t.iff1).isTrue
        assertThat(t.iff2).isFalse
        assertThat(t.im).isEqualTo(2)
        assertThat(t.halted).isFalse
        assertThat(t.tStatesToRun).isEqualTo(4)
        assertThat(t.memory).hasSize(1)
        assertThat(t.memory[0].first).isEqualTo(0x0100)
        assertThat(t.memory[0].second).containsExactly(0x00.toByte())
    }

    @Test
    fun `parses an input with multi-byte and multi-block memory`() {
        val src =
            """
            multi
            0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000
            00 00 0 0 0 0 4
            0000 dd 21 f2 7c -1
            1234 56 -1
            -1
            """
                .trimIndent()
        val t = FuseTestParser.parseInputs(src.lineSequence()).single()
        assertThat(t.memory).hasSize(2)
        assertThat(t.memory[0].first).isEqualTo(0x0000)
        assertThat(t.memory[0].second)
            .containsExactly(0xDD.toByte(), 0x21.toByte(), 0xF2.toByte(), 0x7C.toByte())
        assertThat(t.memory[1].first).isEqualTo(0x1234)
        assertThat(t.memory[1].second).containsExactly(0x56.toByte())
    }

    @Test
    fun `parses many input cases by splitting on blank lines`() {
        val src =
            """
            00
            0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000
            00 00 0 0 0 0 4
            0000 00 -1
            -1

            01
            1111 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000
            00 00 0 0 0 0 4
            0000 01 -1
            -1
            """
                .trimIndent()
        val tests = FuseTestParser.parseInputs(src.lineSequence())
        assertThat(tests.map { it.name }).containsExactly("00", "01")
        assertThat(tests[1].af).isEqualTo(0x1111)
    }

    @Test
    fun `parses an expected state block ignoring events with memory`() {
        // Real FUSE tests.expected format:
        //   line 1: name
        //   event lines: start with whitespace ("    4 MR 0000 02")
        //   state line: 13 hex tokens (same shape as input)
        //   control line: 7 tokens
        //   zero or one memory line "addr byte... -1" (no overall list terminator)
        //   blank line separates tests
        val src =
            """
            02
                0 MC 0000
                4 MR 0000 02
                4 MC 0001
                7 MW 0001 56
            5600 0001 0000 0000 0000 0000 0000 0000 0000 0000 0000 0001 5602
            00 01 0 0 0 0 7
            0001 56 -1
            """
                .trimIndent()
        val expected = FuseTestParser.parseExpected(src.lineSequence())
        assertThat(expected).hasSize(1)
        val e = expected.single()
        assertThat(e.name).isEqualTo("02")
        assertThat(e.af).isEqualTo(0x5600)
        assertThat(e.bc).isEqualTo(0x0001)
        assertThat(e.pc).isEqualTo(0x0001)
        assertThat(e.memptr).isEqualTo(0x5602)
        assertThat(e.i).isEqualTo(0)
        assertThat(e.r).isEqualTo(1)
        assertThat(e.iff1).isFalse
        assertThat(e.iff2).isFalse
        assertThat(e.im).isEqualTo(0)
        assertThat(e.halted).isFalse
        assertThat(e.tStatesAfter).isEqualTo(7)
        assertThat(e.memory).hasSize(1)
        assertThat(e.memory[0].first).isEqualTo(0x0001)
        assertThat(e.memory[0].second).containsExactly(0x56.toByte())
    }

    @Test
    fun `parses an expected case with no memory block`() {
        val src =
            """
            00
                0 MC 0000
                4 MR 0000 00
            0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0001 0000
            00 01 0 0 0 0 4

            01
                0 MC 0000
                4 MR 0000 01
            0000 3412 0000 0000 0000 0000 0000 0000 0000 0000 0000 0003 0000
            00 01 0 0 0 0 10
            """
                .trimIndent()
        val expected = FuseTestParser.parseExpected(src.lineSequence())
        assertThat(expected.map { it.name }).containsExactly("00", "01")
        assertThat(expected[0].memory).isEmpty()
        assertThat(expected[0].tStatesAfter).isEqualTo(4)
        assertThat(expected[1].memory).isEmpty()
        assertThat(expected[1].bc).isEqualTo(0x3412)
        assertThat(expected[1].tStatesAfter).isEqualTo(10)
    }

    @Test
    fun `parseInputs rejects state line with wrong token count`() {
        val src =
            """
            00
            1234 5678 9abc
            0 0 1 4 0 0 0
            0100 00 -1
            -1
            """
                .trimIndent()
        org.assertj.core.api.Assertions.assertThatThrownBy {
                FuseTestParser.parseInputs(src.lineSequence())
            }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("00")
    }

    @Test
    fun `parseInputs rejects unterminated memory block`() {
        // Memory line with no -1 terminator and not followed by another memory line
        val src =
            """
            00
            0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000
            0 0 1 4 0 0 0
            0100 00
            -1
            """
                .trimIndent()
        org.assertj.core.api.Assertions.assertThatThrownBy {
                FuseTestParser.parseInputs(src.lineSequence())
            }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("00")
            .hasMessageContaining("terminator")
    }

    @Test
    fun `parseExpected diagnostic includes test name not the placeholder`() {
        // A malformed memory line in expected — error message should mention "99" not "<expected>"
        val src =
            """
            99
            0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000
            0 0 1 4 0 0 0
            0100 00
            """
                .trimIndent()
        org.assertj.core.api.Assertions.assertThatThrownBy {
                FuseTestParser.parseExpected(src.lineSequence())
            }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("99")
    }

    @Test
    fun `parseExpected captures PR events with port and byte`() {
        val src =
            """
            bigtest
                0 MC 0000
                4 MR 0000 DB
                8 MC 0001
               12 MR 0001 04
               16 PR 4204 99
            0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0002 0000
            00 02 0 0 0 0 11
            """
                .trimIndent()
        val cases = FuseTestParser.parseExpected(src.lineSequence())
        assertThat(cases).hasSize(1)
        val case = cases[0]
        assertThat(case.name).isEqualTo("bigtest")
        assertThat(case.portReads).hasSize(1)
        assertThat(case.portReads[0].port).isEqualTo(0x4204)
        assertThat(case.portReads[0].byte).isEqualTo(0x99)
    }

    @Test
    fun `parseExpected captures multiple PR events in order`() {
        val src =
            """
            multipr
                0 PR 0001 11
                4 PR 0002 22
                8 PR 0003 33
            0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0001 0000
            00 01 0 0 0 0 4
            """
                .trimIndent()
        val case = FuseTestParser.parseExpected(src.lineSequence()).single()
        assertThat(case.portReads).hasSize(3)
        assertThat(case.portReads[0].port).isEqualTo(0x0001)
        assertThat(case.portReads[0].byte).isEqualTo(0x11)
        assertThat(case.portReads[2].port).isEqualTo(0x0003)
        assertThat(case.portReads[2].byte).isEqualTo(0x33)
    }

    @Test
    fun `parseExpected returns empty portReads when no PR events`() {
        val src =
            """
            nopr
                0 MC 0000
                4 MR 0000 00
            0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0000 0001 0000
            00 01 0 0 0 0 4
            """
                .trimIndent()
        val case = FuseTestParser.parseExpected(src.lineSequence()).single()
        assertThat(case.portReads).isEmpty()
    }

    @Test
    fun `parses the entire vendored tests_in file`() {
        val resource =
            requireNotNull(this::class.java.getResourceAsStream("/fuse/tests.in")) {
                "missing /fuse/tests.in resource"
            }
        val tests = resource.bufferedReader().useLines { FuseTestParser.parseInputs(it) }
        assertThat(tests).hasSize(1356)
        assertThat(tests.first().name).isEqualTo("00")
        assertThat(tests.last().name).isEqualTo("ff")
    }

    @Test
    fun `parses the entire vendored tests_expected file`() {
        val resource =
            requireNotNull(this::class.java.getResourceAsStream("/fuse/tests.expected")) {
                "missing /fuse/tests.expected resource"
            }
        val expected = resource.bufferedReader().useLines { FuseTestParser.parseExpected(it) }
        assertThat(expected).hasSize(1356)
        assertThat(expected.first().name).isEqualTo("00")
        assertThat(expected.last().name).isEqualTo("ff")
    }
}
