package com.gathin.tvhome

import com.gathin.tvhome.feature.agent.ui.DuinchAiGreeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuinchAiGreetingTest {

    @Test
    fun trimsTrailingDanglingSentenceFragment() {
        val raw = "Chào buổi trưa bạn nhé! Hãy nghe nhạc thư giãn. Trưa mai, tôi sẽ gợi ý mộ"
        val cleaned = DuinchAiGreeting.sanitizeAiGreeting(raw)
        assertEquals("Chào buổi trưa bạn nhé! Hãy nghe nhạc thư giãn.", cleaned)
        assertTrue(DuinchAiGreeting.isValidGreeting(cleaned))
    }

    @Test
    fun neverCutsWordsInHalfWhenExceedingLengthLimit() {
        val raw = "Chào bạn! Đây là một câu rất dài nhằm kiểm tra tính năng cắt câu an toàn " +
            "không bao giờ làm đứt đôi một từ vựng tiếng Việt nào như một thành mộ."
        val cleaned = DuinchAiGreeting.sanitizeAiGreeting(raw)
        assertFalse(cleaned.endsWith("mộ"))
        assertFalse(cleaned.endsWith(" và"))
        assertFalse(cleaned.endsWith(" hoặc"))
        assertTrue(cleaned.endsWith(".") || cleaned.endsWith("!"))
        assertTrue(DuinchAiGreeting.isValidGreeting(cleaned))
    }

    @Test
    fun removesDanglingConnectorsAndAddsPeriod() {
        val raw = "Chào buổi tối ấm áp nhé bạn và"
        val cleaned = DuinchAiGreeting.sanitizeAiGreeting(raw)
        assertEquals("Chào buổi tối ấm áp nhé bạn.", cleaned)
    }

    @Test
    fun rejectsBlacklistedTerms() {
        assertFalse(DuinchAiGreeting.isValidGreeting("Chào chồng yêu, tối nay xem gì?"))
        assertFalse(DuinchAiGreeting.isValidGreeting("Chào vợ yêu, trưa nay xem gì?"))
        assertFalse(DuinchAiGreeting.isValidGreeting("Hệ thống bị lỗi rồi bạn ơi."))
        assertTrue(DuinchAiGreeting.isValidGreeting("Chào bạn! Chúc bạn xem phim vui vẻ."))
    }

    @Test
    fun rejectsTooShortOrIncompleteSentences() {
        assertFalse(DuinchAiGreeting.isValidGreeting("Chào"))
        assertFalse(DuinchAiGreeting.isValidGreeting("Trưa mai"))
        assertFalse(DuinchAiGreeting.isValidGreeting("Chào bạn nhưng"))
    }

    @Test
    fun cleansLeadingConjunctionAndSplitsRunOnWish() {
        val orphan = DuinchAiGreeting.sanitizeAiGreeting("hoặc chúc bạn một giấc ngủ ngon.")
        assertEquals("Chúc bạn một giấc ngủ ngon.", orphan)

        val runOn = DuinchAiGreeting.sanitizeAiGreeting(
            "Chào buổi tối! Hãy xem phim, nghe nhạc thư giãn hoặc chúc bạn một giấc ngủ ngon."
        )
        assertEquals(
            "Chào buổi tối! Hãy xem phim, nghe nhạc thư giãn. Chúc bạn một giấc ngủ ngon.",
            runOn
        )
    }
}
