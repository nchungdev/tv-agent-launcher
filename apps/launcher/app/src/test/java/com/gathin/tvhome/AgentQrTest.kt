package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.ui.AgentQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentQrTest {

    @Test
    fun testEncodeMatrixGeneratesValidFinderPatterns() {
        val matrix = AgentQr.encode("http://192.168.1.100:8080/setup?token=abcdef123456")
        val side = matrix.size
        assertTrue(side >= 21) // Valid QR side (21, 25, 29, ...)

        // Verify top-left finder pattern
        for (r in 0..6) {
            for (c in 0..6) {
                val shouldBeDark = (r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4))
                assertEquals("Top-left finder at ($r, $c)", shouldBeDark, matrix[r][c])
            }
        }

        // Verify top-right finder pattern
        for (r in 0..6) {
            for (c in 0..6) {
                val shouldBeDark = (r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4))
                assertEquals("Top-right finder at ($r, $c)", shouldBeDark, matrix[r][side - 7 + c])
            }
        }

        // Verify bottom-left finder pattern
        for (r in 0..6) {
            for (c in 0..6) {
                val shouldBeDark = (r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4))
                assertEquals("Bottom-left finder at ($r, $c)", shouldBeDark, matrix[side - 7 + r][c])
            }
        }
    }
}
