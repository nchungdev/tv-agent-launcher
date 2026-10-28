package com.gathin.tvhome

import com.gathin.tvhome.feature.main.widget.WidgetShelfPlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetShelfPlacementTest {
    @Test
    fun reservesAvailableHeightWithoutScalingTheShelf() {
        val bounds = WidgetShelfPlacement.fit(104, 240, 312, 12)
        assertEquals(116, bounds.top)
        assertEquals(112, bounds.height)
        assertTrue(bounds.top + bounds.height <= 228)
    }

    @Test
    fun centersShelfWithoutEnlargingItOnTallScreens() {
        val bounds = WidgetShelfPlacement.fit(104, 700, 312, 12)
        assertEquals(312, bounds.height)
        assertEquals(246, bounds.top)
    }

    @Test
    fun keepsSameProportionsAcrossDisplayDensities() {
        val normal = WidgetShelfPlacement.fit(104, 360, 312, 12)
        val doubled = WidgetShelfPlacement.fit(208, 720, 624, 24)
        assertEquals(normal.height * 2, doubled.height)
        assertEquals(normal.top * 2, doubled.top)
    }

    @Test
    fun handlesViewportWithoutWidgetSpace() {
        val bounds = WidgetShelfPlacement.fit(200, 180, 312, 12)
        assertEquals(0, bounds.height)
    }
}
