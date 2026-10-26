package com.gathin.tvhome.feature.agent.coordinator
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.ui.*
import com.gathin.tvhome.feature.agent.widget.*

import android.content.Context
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import java.util.Calendar
import java.util.Locale


object DuinchBrain {

    const val VIEWER_KEY = "duinch_viewer"
    const val VIEWER_ASK = 0
    const val VIEWER_HUSBAND = 1
    const val VIEWER_WIFE = 2
    const val VIEWER_COUPLE = 3
    const val VIEWER_FAMILY = 4
    const val VIEWER_COUNT = 5

    @JvmStatic
    fun viewer(context: Context): Int {
        val stored = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
            .getInt(VIEWER_KEY, VIEWER_ASK)
        return if (stored < 0 || stored >= VIEWER_COUNT) VIEWER_ASK else stored
    }

    @JvmStatic
    fun setViewer(context: Context, nextViewer: Int) {
        val clamped = nextViewer.coerceIn(0, VIEWER_COUNT - 1)
        context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(VIEWER_KEY, clamped).apply()
    }

    @JvmStatic
    fun viewerLabel(context: Context, profile: Int): String {
        return when (profile) {
            VIEWER_HUSBAND -> context.getString(R.string.duinch_viewer_husband)
            VIEWER_WIFE -> context.getString(R.string.duinch_viewer_wife)
            VIEWER_COUPLE -> context.getString(R.string.duinch_viewer_couple)
            VIEWER_FAMILY -> context.getString(R.string.duinch_viewer_family)
            else -> context.getString(R.string.duinch_viewer_ask)
        }
    }

    @JvmStatic
    fun detectViewerFromSpeech(transcript: String?): Int {
        if (transcript == null) return -1
        val lower = transcript.lowercase(Locale.ROOT)
        if (lower.contains("hai vợ chồng") || lower.contains("cả hai") || lower.contains("hai đứa")) return VIEWER_COUPLE
        if (lower.contains("gia đình") || lower.contains("cả nhà") || lower.contains("em bé") || lower.contains("con đây")) return VIEWER_FAMILY
        if (lower.contains("chồng") || lower.contains("anh đây") || lower.contains("bố đây") || lower.contains("mình là anh")) return VIEWER_HUSBAND
        if (lower.contains("vợ đây") || lower.contains("em đây") || lower.contains("mẹ đây") || lower.contains("chị đây")) return VIEWER_WIFE
        return -1
    }

    @JvmStatic
    fun thinkingLine(context: Context): String = context.getString(R.string.duinch_thinking_line)

    @JvmStatic
    fun homeThought(context: Context, step: Int): String {
        val currentViewer = viewer(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (currentViewer == VIEWER_ASK || step % 3 == 0) askViewerLine(hour) else recommendationLine(hour, currentViewer)
    }

    @JvmStatic
    fun agentGreeting(context: Context): String {
        val currentViewer = viewer(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (currentViewer == VIEWER_ASK) askViewerLine(hour) else recommendationLine(hour, currentViewer)
    }

    private fun askViewerLine(hour: Int): String {
        return when {
            hour in 5..10 -> "Chào buổi sáng! Ai đang bật TV thế nhỉ: Anh, Chị hay cả nhà mình?"
            hour in 11..13 -> "Giờ nghỉ trưa rồi! Ai đang xem TV đấy, nói cho Duinch biết để gợi ý nhé!"
            hour in 14..17 -> "Buổi chiều thư giãn nè! Đang cầm remote là Anh, Chị hay cả hai vợ chồng?"
            hour in 18..21 -> "Giờ vàng xem phim tối nay! Ai đang trước màn hình thế: Anh, Chị hay cả hai?"
            else -> "Đêm khuya rồi đó! Ai đang thức xem TV thế, để Duinch chọn nội dung dịu mắt cho?"
        }
    }

    private fun recommendationLine(hour: Int, profile: Int): String {
        return when {
            hour in 5..10 -> morningLine(profile)
            hour in 11..13 -> noonLine(profile)
            hour in 14..17 -> afternoonLine(profile)
            hour in 18..21 -> eveningLine(profile)
            else -> lateNightLine(profile)
        }
    }

    private fun morningLine(profile: Int): String {
        return when (profile) {
            VIEWER_HUSBAND -> "Sáng nay Anh muốn điểm tin công nghệ mới hay mở nhạc Cafe trên YouTube?"
            VIEWER_WIFE -> "Chào buổi sáng! Mở nhạc Acoustic nhẹ nhàng hay xem vlog nấu ăn nhé?"
            VIEWER_COUPLE -> "Buổi sáng tốt lành! Hai vợ chồng mở nhạc chill ăn sáng cùng nhau nhé?"
            else -> "Buổi sáng vui vẻ! Mở nhạc thiếu nhi sôi động hay khám phá thiên nhiên nào?"
        }
    }

    private fun noonLine(profile: Int): String {
        return when (profile) {
            VIEWER_HUSBAND -> "Nghỉ trưa rồi Anh ơi! Xem nhanh một tập Dragon Ball hay video công nghệ nhé?"
            VIEWER_WIFE -> "Giờ trưa rồi nè! Xem show giải trí vui nhộn hay vlog ẩm thực vừa ăn vừa xem?"
            VIEWER_COUPLE -> "Bữa trưa vui vẻ! Hai vợ chồng mở hài ngắn hay sitcom xem cùng nhau nhé?"
            else -> "Giờ nghỉ trưa rồi! Cả nhà mình xem hoạt hình vui nhộn trên YouTube nhé?"
        }
    }

    private fun afternoonLine(profile: Int): String {
        return when (profile) {
            VIEWER_HUSBAND -> "Chiều nay Anh muốn nghe nhạc Lofi tập trung hay xem highlight thể thao?"
            VIEWER_WIFE -> "Buổi chiều thư giãn nè! Mở nhạc chill hay xem vlog du lịch Hàn Quốc nhé?"
            VIEWER_COUPLE -> "Chiều mát mẻ! Hai vợ chồng nghe playlist nhạc đôi hay xem chương trình khám phá?"
            else -> "Buổi chiều vui nhộn! Mở thế giới động vật hay hoạt hình phiêu lưu cho cả nhà nhé?"
        }
    }

    private fun eveningLine(profile: Int): String {
        return when (profile) {
            VIEWER_HUSBAND -> "Tối rồi Anh ơi! Lên Plex cày phim hành động bom tấn hay xem Dragon Ball tiếp?"
            VIEWER_WIFE -> "Buổi tối thư giãn rồi! Xem phim tình cảm lãng mạn hay show âm nhạc nhé?"
            VIEWER_COUPLE -> "Giờ vàng buổi tối! Hai vợ chồng mở Plex xem phim điện ảnh hay hài giải trí nào?"
            else -> "Buổi tối sum vầy! Cả nhà mình cùng xem phim hoạt hình gia đình trên Plex nhé?"
        }
    }

    private fun lateNightLine(profile: Int): String {
        return when (profile) {
            VIEWER_HUSBAND -> "Khuya rồi Anh ơi! Xem phim Sci-Fi nhẹ nhàng hay nghe nhạc Lofi rồi nghỉ sớm nhé?"
            VIEWER_WIFE -> "Đêm muộn rồi nè! Mở nhạc piano thư giãn dễ ngủ để giữ gìn sức khoẻ nhé!"
            VIEWER_COUPLE -> "Khuya rồi hai vợ chồng ơi! Xem nốt tập phim tâm lý nhẹ nhàng rồi đi ngủ sớm nha!"
            else -> "Đã khuya rồi, mở nhạc ru ngủ êm dịu rồi cả nhà mình cùng đi ngủ ngon nhé!"
        }
    }

    @JvmStatic
    fun suggestions(context: Context): Array<String> {
        val currentViewer = viewer(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (currentViewer == VIEWER_WIFE) {
            if (hour in 18..21) {
                return arrayOf("Phim tình cảm tối nay", "Show âm nhạc YouTube", "Vlog du lịch ẩm thực")
            }
            if (hour >= 22 || hour < 5) {
                return arrayOf("Nhạc piano dễ ngủ", "Phim chữa lành nhẹ nhàng", "Thời tiết ngày mai")
            }
            return arrayOf("Nhạc Acoustic nhẹ nhàng", "Gợi ý món ngon hôm nay", "Phim lãng mạn hay")
        }
        if (currentViewer == VIEWER_COUPLE) {
            if (hour in 18..21) {
                return arrayOf("Phim điện ảnh tối nay", "Mở Plex xem phim", "Hài giải trí YouTube")
            }
            return arrayOf("Phim hài lãng mạn", "Nhạc chill YouTube", "Phim hay cuối tuần")
        }
        if (currentViewer == VIEWER_FAMILY) {
            return arrayOf("Phim hoạt hình gia đình", "Khám phá thiên nhiên", "Kể chuyện vui gia đình")
        }
        if (hour in 5..10) {
            return arrayOf("Tin công nghệ hôm nay", "Nhạc cafe sáng", "Thời tiết hôm nay")
        }
        if (hour in 18..21) {
            return arrayOf("Phim hành động bom tấn", "Tìm phim Dragon Ball", "Mở Plex xem phim")
        }
        if (hour >= 22 || hour < 5) {
            return arrayOf("Phim Sci-Fi đêm khuya", "Nhạc Lofi dễ ngủ", "Dung lượng máy chủ")
        }
        return arrayOf("Tìm phim Dragon Ball", "Tin công nghệ hôm nay", "Video giải trí chiều")
    }
}
