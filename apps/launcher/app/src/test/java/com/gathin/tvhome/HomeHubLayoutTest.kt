package com.gathin.tvhome

import com.gathin.tvhome.feature.main.widget.HomeHubLayout
import org.junit.Assert.*
import org.junit.Test

class HomeHubLayoutTest {
    @Test fun usesAvailableSpaceRatherThanPixelResolution() {
        assertTrue(HomeHubLayout.expanded(1920, 650, 2f))
        assertFalse(HomeHubLayout.expanded(2340, 600, 2.8125f))
        assertFalse(HomeHubLayout.expanded(3840, 900, 4f))
        assertFalse(HomeHubLayout.expanded(1280, 550, 2f))
        assertTrue(HomeHubLayout.expanded(1280, 400, 1.3f))
    }
}
