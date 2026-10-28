package com.gathin.tvhome

import com.gathin.tvhome.feature.main.data.SkyPlacement
import org.junit.Assert.*
import org.junit.Test

class SkyPlacementTest {
    @Test fun sceneFitsAroundWidgetsAtDifferentScreenSizes() {
        for (width in listOf(1280f, 1920f, 2340f, 3840f)) {
            val height = width * 9 / 16
            val area = SkyPlacement.Box(20f, height * 0.2f, width - 20, height * 0.75f)
            val blocked = listOf(
                SkyPlacement.Box(width * 0.08f, height * 0.35f, width * 0.43f, height * 0.6f),
                SkyPlacement.Box(width * 0.65f, height * 0.35f, width * 0.94f, height * 0.58f)
            )
            val layout = SkyPlacement.arrange(area, blocked, 3, width / 960)
            val shapes = listOfNotNull(layout.celestial) + layout.clouds
            assertNotNull(layout.celestial)
            for (shape in shapes) {
                assertTrue(shape.left >= area.left && shape.top >= area.top)
                assertTrue(shape.right <= area.right && shape.bottom <= area.bottom)
                assertFalse(blocked.any { shape.overlaps(it) })
            }
            assertEquals(layout, SkyPlacement.arrange(area, blocked, 3, width / 960))
        }
    }

    @Test fun crowdedLayoutDropsDecorationsWithoutShrinkingWidgets() {
        val area = SkyPlacement.Box(0f, 0f, 200f, 100f)
        val layout = SkyPlacement.arrange(area, listOf(area), 3, 1f)
        assertNull(layout.celestial)
        assertTrue(layout.clouds.isEmpty())
    }
}
