package com.gathin.tvhome

import com.gathin.tvhome.feature.main.widget.WidgetShelfPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetShelfPlacementTest {
    @Test
    fun shrinksShelfToFitShortLandscapeScreen() {
        val bounds = WidgetShelfPlacement.fit(104, 240, 312, 12)
        assertEquals(116, bounds.top)
        assertEquals(112f / 312, bounds.scale, 0.001f)
        assertTrue(bounds.top + 312 * bounds.scale <= 228)
    }

    @Test
    fun centersShelfWithoutEnlargingItOnTallScreens() {
        val bounds = WidgetShelfPlacement.fit(104, 700, 312, 12)
        assertEquals(1f, bounds.scale, 0f)
        assertEquals(246, bounds.top)
    }

    @Test
    fun keepsSameProportionsAcrossDisplayDensities() {
        val normal = WidgetShelfPlacement.fit(104, 360, 312, 12)
        val doubled = WidgetShelfPlacement.fit(208, 720, 624, 24)
        assertEquals(normal.scale, doubled.scale, 0f)
        assertEquals(normal.top * 2, doubled.top)
    }

    @Test
    fun handlesViewportWithoutWidgetSpace() {
        val bounds = WidgetShelfPlacement.fit(200, 180, 312, 12)
        assertEquals(0f, bounds.scale, 0f)
    }
}
