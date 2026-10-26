package com.gathin.tvhome.feature.agent.ui

import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.widget.*
import com.gathin.tvhome.feature.agent.adapter.*
import com.gathin.tvhome.feature.agent.data.AgentClient
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object DuinchAiGreeting {

    private const val REFRESH_INTERVAL_MS = 20 * 60 * 1000L
    private const val FAILURE_COOLDOWN_MS = 10 * 60 * 1000L
    private const val MAX_GREETING_LENGTH = 140
    private const val MAX_PUSH_GREETING_LENGTH = 240
    private const val MIN_GREETING_LENGTH = 5

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val isFetching = AtomicBoolean(false)
    private val promptVariantCounter = AtomicInteger(0)
    private var activeRequest: AgentClient.Request? = null

    @Volatile
    private var memoryGreeting: String? = null
    @Volatile
    private var memoryGreetingTime: Long = 0L
    @Volatile
    private var memoryGreetingPeriod: String? = null
    @Volatile
    private var memoryFailureTime: Long = 0L
    @Volatile
    private var pushedFromServer: Boolean = false

    private val LEADING_CONJUNCTION_REGEX = Regex(
        "^(hoặc là|hay là|hoặc|hay|và|nhưng mà|nhưng|mà|rồi)\\s+",
        RegexOption.IGNORE_CASE
    )
    private val RUN_ON_WISH_REGEX = Regex(
        "[,;]?\\s+(hoặc là|hay là|hoặc|hay|và)\\s+(chúc\\s+)",
        RegexOption.IGNORE_CASE
    )

    fun defaultGreeting(name: String): String {
        val displayName = name.trim()
        val target = if (displayName.isEmpty()) "bạn" else displayName
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..10 ->
                "Chào buổi sáng $target! Chúc một ngày mới tràn đầy năng lượng và niềm vui."
            hour in 11..13 ->
                "Chào buổi trưa $target! Nghỉ ngơi thư giãn và dùng bữa trưa thật ngon miệng nhé."
            hour in 14..17 ->
                "Chào buổi chiều $target! Mở một bản nhạc nhẹ nhàng để thư giãn tinh thần nhé."
            hour in 18..21 ->
                "Chào buổi tối $target! Tận hưởng khoảng thời gian ấm cúng và thư thái bên TV nhé."
            else ->
                "Đêm đã khuya rồi $target ơi. Nhớ nghỉ ngơi sớm và chúc bạn một giấc ngủ thật ngon!"
        }
    }

    fun greeting(
        context: Context,
        name: String,
        onAiReady: (String) -> Unit
    ): String {
        val currentPeriod = timePeriodDescription()
        val now = System.currentTimeMillis()

        requestAiGreetingIfNeeded(context, name, now, currentPeriod, onAiReady)

        val cached = memoryGreeting
        if (!cached.isNullOrEmpty() &&
            (pushedFromServer || isValidGreeting(cached)) &&
            memoryGreetingPeriod == currentPeriod &&
            now - memoryGreetingTime < REFRESH_INTERVAL_MS
        ) {
            return cached
        }
        return defaultGreeting(name)
    }

    fun interactiveThought(name: String, step: Int = 0): String {
        val displayName = name.trim()
        val target = if (displayName.isEmpty()) "bạn" else displayName
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val options = when {
            hour in 5..10 -> arrayOf(
                "Chào buổi sáng $target! Nhấn OK vào Duinch nếu cần mở nhạc hay hỏi thời tiết nhé.",
                "Sáng nay $target muốn nghe nhạc Acoustic nhẹ nhàng hay điểm tin công nghệ mới?"
            )
            hour in 11..13 -> arrayOf(
                "Giờ nghỉ trưa rồi $target ơi! Mở một video giải trí nhẹ nhàng để thư giãn nhé.",
                "Duinch luôn sẵn sàng hỗ trợ $target điều khiển nhà thông minh và tìm nội dung hay."
            )
            hour in 14..17 -> arrayOf(
                "Buổi chiều thư thái nhé $target! Cần chỉnh quạt hay mở nhạc Lofi cứ gọi Duinch nha.",
                "Chiều nay $target muốn nghe playlist nhạc chill hay xem chương trình khám phá?"
            )
            hour in 18..21 -> arrayOf(
                "Buổi tối ấm áp nhé $target! Lên Plex chọn một bộ phim hay để thưởng thức thôi nào.",
                "Nhấn OK vào Duinch để trò chuyện hoặc điều khiển nhanh các thiết bị trong phòng nhé."
            )
            else -> arrayOf(
                "Khuya rồi $target ơi! Mở nhạc êm dịu thư giãn rồi nghỉ ngơi sớm giữ sức khỏe nhé.",
                "Đêm muộn rồi, chúc $target có những phút giây thật bình yên và ngủ thật ngon giấc."
            )
        }
        val idx = (step.coerceAtLeast(0)) % options.size
        return options[idx]
    }

    fun pushGreeting(rawText: String): String {
        val cleaned = sanitizeAiGreeting(rawText, MAX_PUSH_GREETING_LENGTH)
        val accepted = if (cleaned.isNotEmpty()) cleaned else rawText.trim()
        if (accepted.isNotEmpty()) {
            memoryGreeting = accepted
            memoryGreetingPeriod = timePeriodDescription()
            memoryGreetingTime = System.currentTimeMillis()
            pushedFromServer = true
        }
        return accepted
    }

    fun cancel() {
        activeRequest?.cancel()
        activeRequest = null
        isFetching.set(false)
    }

    private fun requestAiGreetingIfNeeded(
        context: Context,
        name: String,
        now: Long,
        currentPeriod: String,
        onAiReady: (String) -> Unit
    ) {
        if (memoryGreetingPeriod == currentPeriod &&
            now - memoryGreetingTime < REFRESH_INTERVAL_MS
        ) {
            return
        }

        if (now - memoryFailureTime < FAILURE_COOLDOWN_MS) return

        if (!isFetching.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        val targetName = if (name.isBlank()) "bạn" else name.trim()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val variant = promptVariantCounter.getAndIncrement()
        val topicHint = timeSlotTopic(hour, variant)
        val prompt = "Bạn là Duinch - trợ lý ảo thông minh trên TV phòng khách. " +
            "Hãy viết 1 hoặc 2 câu chào ngắn gọn (12 đến 22 từ), tự nhiên, ấm áp gửi đến " +
            "$targetName vào $currentPeriod. " +
            "Nội dung trọng tâm duy nhất: $topicHint " +
            "Tuyệt đối KHÔNG dùng từ 'hoặc', 'hay là' để ghép nhiều ý rời rạc trong một câu. " +
            "Giữ nguyên tên '$targetName', không đổi thành vợ chồng hay người yêu. " +
            "Câu phải tròn ý, ngữ pháp tự nhiên, kết thúc bằng dấu chấm hoặc chấm than, " +
            "không dùng ngoặc kép, không markdown."

        executor.execute {
            executeAiRequest(appContext, prompt, currentPeriod, onAiReady)
        }
    }

    private fun timeSlotTopic(hour: Int, variant: Int): String {
        val even = variant % 2 == 0
        return when {
            hour in 5..10 -> if (even) {
                "Chúc một ngày mới tràn đầy năng lượng và khởi đầu thuận lợi."
            } else {
                "Gợi ý mở một bản nhạc nhẹ nhàng để bắt đầu buổi sáng thư thái."
            }
            hour in 11..13 -> if (even) {
                "Chúc bữa trưa ngon miệng và có thời gian nghỉ ngơi dễ chịu."
            } else {
                "Gợi ý thư giãn buổi trưa với một chương trình giải trí nhẹ nhàng."
            }
            hour in 14..17 -> if (even) {
                "Gợi ý nghe một bản nhạc Lofi êm dịu cho buổi chiều thư giãn."
            } else {
                "Chúc buổi chiều làm việc và nghỉ ngơi thật thoải mái, nhẹ nhàng."
            }
            hour in 18..21 -> if (even) {
                "Gợi ý thưởng thức một bộ phim hấp dẫn để thư giãn buổi tối."
            } else {
                "Chúc một buổi tối ấm cúng, bình yên và trọn vẹn niềm vui bên gia đình."
            }
            else -> if (even) {
                "Nhắc đêm đã khuya, khuyên nghỉ ngơi sớm và chúc một giấc ngủ thật ngon."
            } else {
                "Chúc những giây phút cuối ngày thật êm dịu và một giấc ngủ sâu."
            }
        }
    }

    private fun executeAiRequest(
        context: Context,
        prompt: String,
        period: String,
        onAiReady: (String) -> Unit
    ) {
        val request = AgentClient.Request()
        activeRequest = request
        val buffer = StringBuilder()
        var succeeded = false

        try {
            AgentClient.ask(context, "home_greeting", prompt, { chunk ->
                if (buffer.length < MAX_GREETING_LENGTH * 2) {
                    buffer.append(chunk)
                }
            }, request)

            val cleaned = sanitizeAiGreeting(buffer.toString())
            if (isValidGreeting(cleaned)) {
                memoryGreeting = cleaned
                memoryGreetingPeriod = period
                memoryGreetingTime = System.currentTimeMillis()
                succeeded = true
                mainHandler.post { onAiReady(cleaned) }
            }
        } catch (_: Exception) {
        } finally {
            if (!succeeded) {
                memoryFailureTime = System.currentTimeMillis()
            }
            activeRequest = null
            isFetching.set(false)
        }
    }

    internal fun sanitizeAiGreeting(raw: String, maxLength: Int = MAX_GREETING_LENGTH): String {
        var text = raw.trim()
            .replace("\"", "")
            .replace("“", "")
            .replace("”", "")
            .replace("'", "")
            .replace("*", "")
            .replace("#", "")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (text.startsWith("-")) {
            text = text.substring(1).trim()
        }

        text = LEADING_CONJUNCTION_REGEX.replaceFirst(text, "").trim()
        text = RUN_ON_WISH_REGEX.replace(text, ". Chúc ")

        val lastTerminal = maxOf(
            text.lastIndexOf('.'),
            text.lastIndexOf('!'),
            text.lastIndexOf('?')
        )
        if (lastTerminal in MIN_GREETING_LENGTH until text.length) {
            text = text.substring(0, lastTerminal + 1).trim()
        }

        if (text.length > maxLength) {
            val lastPunct = maxOf(
                text.lastIndexOf('.', maxLength),
                text.lastIndexOf('!', maxLength),
                text.lastIndexOf('?', maxLength)
            )
            if (lastPunct >= maxLength / 2) {
                text = text.substring(0, lastPunct + 1).trim()
            } else {
                val lastSpace = text.lastIndexOf(' ', maxLength)
                if (lastSpace >= MIN_GREETING_LENGTH) {
                    text = text.substring(0, lastSpace).trim()
                } else {
                    text = text.substring(0, maxLength).trim()
                }
            }
        }

        val dangling = arrayOf(
            " và", " hoặc", " hay", " của", " với", " nhưng", " là", " để", " một", " rồi"
        )
        for (suffix in dangling) {
            if (text.endsWith(suffix, ignoreCase = true)) {
                text = text.substring(0, text.length - suffix.length).trim()
            }
        }

        if (text.isNotEmpty() && !text.endsWith(".") && !text.endsWith("!") &&
            !text.endsWith("?")
        ) {
            text = "$text."
        }
        if (text.isNotEmpty() && text[0].isLowerCase()) {
            text = text[0].uppercaseChar() + text.substring(1)
        }
        return text
    }

    internal fun isValidGreeting(text: String): Boolean {
        if (text.length !in MIN_GREETING_LENGTH..MAX_GREETING_LENGTH) return false
        if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?")) return false
        if (LEADING_CONJUNCTION_REGEX.containsMatchIn(text)) return false
        val words = text.split(" ").filter { it.isNotBlank() }
        if (words.size < 2) return false
        val lower = text.lowercase(Locale.ROOT)
        val blacklisted = arrayOf(
            "tạm hoãn", "lỗi", "error", "busy", "thất bại", "không thể",
            "chồng yêu", "vợ yêu", "anh yêu", "em yêu", "người yêu", "ăn gì", "cơm chưa",
            "hoặc lời chúc", "hay lời chúc"
        )
        return blacklisted.none { lower.contains(it) }
    }

    private fun timePeriodDescription(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..11 -> "buổi sáng"
            hour in 12..14 -> "buổi trưa"
            hour in 15..17 -> "buổi chiều"
            hour in 18..21 -> "buổi tối"
            else -> "đêm muộn"
        }
    }
}
