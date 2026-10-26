package com.gathin.tvhome.feature.agent.coordinator
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.ui.*
import com.gathin.tvhome.feature.agent.widget.*

import java.util.regex.Pattern


object AnswerPresentation {
    private val RICH = Pattern.compile(
        "(?is)<(?:div|img|table|iframe|video|a|p|ul|ol|li|h[1-6]|span)\\b" +
                "|(?m)^\\s*\\|.*\\|\\s*$" +
                "|(?m)^\\s*#{1,6}\\s+" +
                "|(?m)^\\s*\\d+\\.\\s+" +
                "|(?m)^\\s*[-*+]\\s+" +
                "|```"
    )
    private val HEADING = Pattern.compile("(?m)^#{1,6}\\s+")
    private val EMPHASIS = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__")
    private val LINK = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^)]+)\\)")

    @JvmStatic
    fun needsBrowser(text: String): Boolean {
        return text.contains("![") || RICH.matcher(text).find()
    }

    @JvmStatic
    fun plainText(text: String): String {
        val headings = HEADING.matcher(text).replaceAll("")
        val emphasis = EMPHASIS.matcher(headings).replaceAll("$1$2")
        return LINK.matcher(emphasis).replaceAll("$1 ($2)")
    }
}
