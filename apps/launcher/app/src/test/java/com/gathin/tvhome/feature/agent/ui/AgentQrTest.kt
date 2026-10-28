package com.gathin.tvhome.feature.agent.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentQrTest {

    private val samples = listOf(
        "http://192.168.1.37:8788/pair?code=4R6PTV",
        "http://192.168.1.8:8789/#0123456789abcdef0123456789abcdef",
        "x".repeat(150),
        "y".repeat(200)
    )

    @Test
    fun bothFormatCopiesAgree() {
        for (text in samples) {
            val m = AgentQr.encode(text)
            assertEquals(text, topLeftFormat(m), otherFormat(m))
        }
    }

    @Test
    fun darkModuleSitsAboveBottomLeftFinder() {
        for (text in samples) {
            val m = AgentQr.encode(text)
            assertTrue(text, m[m.size - 8][8])
        }
    }

    @Test
    fun alignmentOnTimingLineIsDrawnFromVersionSeven() {
        val m = AgentQr.encode("x".repeat(150))
        val version = (m.size - 17) / 4
        assertTrue(version >= 7)
        assertTrue(m[6][24] && m[6][20] && !m[6][21] && !m[6][23] && m[6][22])
    }

    private fun topLeftFormat(m: Array<BooleanArray>): List<Boolean> =
        (0..5).map { m[it][8] } + listOf(m[7][8], m[8][8], m[8][7]) + (9..14).map { m[8][14 - it] }

    private fun otherFormat(m: Array<BooleanArray>): List<Boolean> =
        (0..7).map { m[8][m.size - 1 - it] } + (8..14).map { m[m.size - 15 + it][8] }
}
