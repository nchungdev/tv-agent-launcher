package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.coordinator.DuinchBrain
import com.gathin.tvhome.feature.agent.ui.AgentVoiceReply
import org.junit.Assert.assertEquals
import org.junit.Test

class DuinchBrainTest {

    @Test
    fun detectViewerHusband() {
        assertEquals(DuinchBrain.VIEWER_HUSBAND, DuinchBrain.detectViewerFromSpeech("anh đây"))
        assertEquals(DuinchBrain.VIEWER_HUSBAND, DuinchBrain.detectViewerFromSpeech("mình là anh"))
        assertEquals(DuinchBrain.VIEWER_HUSBAND, DuinchBrain.detectViewerFromSpeech("bố đây nhé"))
        assertEquals(DuinchBrain.VIEWER_HUSBAND, DuinchBrain.detectViewerFromSpeech("chồng xem TV"))
    }

    @Test
    fun detectViewerWife() {
        assertEquals(DuinchBrain.VIEWER_WIFE, DuinchBrain.detectViewerFromSpeech("vợ đây nè"))
        assertEquals(DuinchBrain.VIEWER_WIFE, DuinchBrain.detectViewerFromSpeech("em đây"))
        assertEquals(DuinchBrain.VIEWER_WIFE, DuinchBrain.detectViewerFromSpeech("mẹ đây"))
        assertEquals(DuinchBrain.VIEWER_WIFE, DuinchBrain.detectViewerFromSpeech("chị đây"))
    }

    @Test
    fun detectViewerCouple() {
        val message = "hai vợ chồng cùng xem"
        assertEquals(DuinchBrain.VIEWER_COUPLE, DuinchBrain.detectViewerFromSpeech(message))
        assertEquals(DuinchBrain.VIEWER_COUPLE, DuinchBrain.detectViewerFromSpeech("cả hai đứa"))
        assertEquals(DuinchBrain.VIEWER_COUPLE, DuinchBrain.detectViewerFromSpeech("cả hai xem"))
    }

    @Test
    fun detectViewerFamily() {
        assertEquals(DuinchBrain.VIEWER_FAMILY, DuinchBrain.detectViewerFromSpeech("cả nhà cùng xem"))
        assertEquals(DuinchBrain.VIEWER_FAMILY, DuinchBrain.detectViewerFromSpeech("gia đình mình"))
        val babyPrompt = "em bé muốn xem hoạt hình"
        assertEquals(DuinchBrain.VIEWER_FAMILY, DuinchBrain.detectViewerFromSpeech(babyPrompt))
        assertEquals(DuinchBrain.VIEWER_FAMILY, DuinchBrain.detectViewerFromSpeech("con đây"))
    }

    @Test
    fun detectViewerUnknownOrNull() {
        assertEquals(-1, DuinchBrain.detectViewerFromSpeech(null))
        assertEquals(-1, DuinchBrain.detectViewerFromSpeech(""))
        val weatherPrompt = "thời tiết hôm nay thế nào"
        assertEquals(-1, DuinchBrain.detectViewerFromSpeech(weatherPrompt))
        assertEquals(-1, DuinchBrain.detectViewerFromSpeech("bật đèn phòng khách"))
    }

    @Test
    fun speedLabelsMatchSpeedOptions() {
        assertEquals("1×", AgentVoiceReply.speedLabel(0))
        assertEquals("1.25×", AgentVoiceReply.speedLabel(1))
        assertEquals("1.5×", AgentVoiceReply.speedLabel(2))
        assertEquals("1.75×", AgentVoiceReply.speedLabel(3))
        assertEquals("2×", AgentVoiceReply.speedLabel(4))
    }
}
