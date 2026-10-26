package com.gathin.tvhome

import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.feature.notification.widget.NotificationHudOverlay
import com.gathin.tvhome.feature.agent.widget.DuinchView
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationHudOverlayTest {

    @Test
    fun resolveMoodMatchesSeverityAndSource() {
        assertEquals(DuinchView.SURPRISED, NotificationHudOverlay.resolveMood("warning", "smarthome"))
        assertEquals(DuinchView.SURPRISED, NotificationHudOverlay.resolveMood("error", "nas"))
        assertEquals(DuinchView.WINK, NotificationHudOverlay.resolveMood("info", "suggestion"))
        assertEquals(DuinchView.EXCITED, NotificationHudOverlay.resolveMood("info", "nas"))
    }

    @Test
    fun formatTagCombinesSourceAndTitleCleanly() {
        val nasNotif = TvNotification(
            id = "1",
            source = "nas",
            title = "Plex",
            message = "Dune 2 đã tải xong",
            severity = "info"
        )
        assertEquals("NAS • PLEX", NotificationHudOverlay.formatTag(nasNotif))

        val genericNotif = TvNotification(
            id = "2",
            source = "smarthome",
            title = "Thông báo",
            message = "Cửa ban công đang mở",
            severity = "warning"
        )
        assertEquals("SMARTHOME", NotificationHudOverlay.formatTag(genericNotif))
    }
}
