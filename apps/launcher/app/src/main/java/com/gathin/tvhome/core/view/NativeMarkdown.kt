package com.gathin.tvhome.core.view

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import java.util.regex.Pattern

data class AgentCardData(
    val status: String,
    val title: String,
    val type: String,
    val year: String,
    val link: String,
    val posterUrl: String = ""
)

data class MarkdownParseResult(
    val formattedText: CharSequence,
    val cards: List<AgentCardData>
)

object NativeMarkdown {

    private val CARD_PATTERN = Pattern.compile(
        "(?s)<div[^>]*?class=[\"'][^\"']*?card[^\"']*?[\"'][^>]*?>.*?</div>"
    )
    private val ATTR_STATUS = Pattern.compile("data-status=[\"']([^\"']*)[\"']")
    private val ATTR_TITLE = Pattern.compile("data-title=[\"']([^\"']*)[\"']")
    private val ATTR_TYPE = Pattern.compile("data-type=[\"']([^\"']*)[\"']")
    private val ATTR_YEAR = Pattern.compile("data-year=[\"']([^\"']*)[\"']")
    private val ATTR_LINK = Pattern.compile("data-link=[\"']([^\"']*)[\"']")
    private val POSTER_IMG = Pattern.compile("<img[^>]*?src=[\"']([^\"']*)[\"']")

    private val BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__")
    private val ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
    private val CODE_PATTERN = Pattern.compile("`([^`\n]+)`")
    private val LINK_PATTERN = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^)]+)\\)")
    private val HEADING_LINE = Pattern.compile("(?m)^(#{1,4})\\s+(.+?)$")
    private val BULLET_LINE = Pattern.compile("(?m)^[ \t]*[-*+][ \t]+(.+)$")

    fun parse(raw: String): MarkdownParseResult {
        if (raw.isBlank()) {
            return MarkdownParseResult("", emptyList())
        }
        val cards = mutableListOf<AgentCardData>()
        val cardMatcher = CARD_PATTERN.matcher(raw)
        val cleanTextBuilder = StringBuffer()

        while (cardMatcher.find()) {
            val html = cardMatcher.group()
            val status = extractAttribute(ATTR_STATUS, html)
            val title = extractAttribute(ATTR_TITLE, html)
            val type = extractAttribute(ATTR_TYPE, html)
            val year = extractAttribute(ATTR_YEAR, html)
            val link = extractAttribute(ATTR_LINK, html)
            val poster = extractAttribute(POSTER_IMG, html)
            if (title.isNotEmpty()) {
                cards.add(AgentCardData(status, title, type, year, link, poster))
            }
            cardMatcher.appendReplacement(cleanTextBuilder, "")
        }
        cardMatcher.appendTail(cleanTextBuilder)
        val textWithoutCards = cleanTextBuilder.toString().trim()
        val formatted = formatMarkdown(textWithoutCards)
        return MarkdownParseResult(formatted, cards)
    }

    private fun extractAttribute(pattern: Pattern, html: String): String {
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1).orEmpty() else ""
    }

    private fun formatMarkdown(source: String): CharSequence {
        var text = source
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
            .replace("</?(?:p|div|span)[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")

        val builder = SpannableStringBuilder(text)
        applyHeadings(builder)
        applyRegexPattern(builder, BOLD_PATTERN) { StyleSpan(Typeface.BOLD) }
        applyRegexPattern(builder, ITALIC_PATTERN) { StyleSpan(Typeface.ITALIC) }
        applyCodeSpans(builder)
        applyLinks(builder)
        applyBullets(builder)
        return builder
    }

    private fun applyHeadings(builder: SpannableStringBuilder) {
        var matcher = HEADING_LINE.matcher(builder)
        while (matcher.find()) {
            val hashes = matcher.group(1) ?: "#"
            val content = matcher.group(2) ?: ""
            val start = matcher.start()
            val end = matcher.end()
            val scale = when (hashes.length) {
                1 -> 1.35f
                2 -> 1.22f
                3 -> 1.12f
                else -> 1.05f
            }
            builder.replace(start, end, content)
            val newEnd = start + content.length
            builder.setSpan(
                RelativeSizeSpan(scale),
                start,
                newEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                newEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            matcher = HEADING_LINE.matcher(builder)
        }
    }

    private fun applyRegexPattern(
        builder: SpannableStringBuilder,
        pattern: Pattern,
        createSpan: () -> Any
    ) {
        var matcher = pattern.matcher(builder)
        while (matcher.find()) {
            val fullMatch = matcher.group(0) ?: ""
            val captured = matcher.group(1) ?: matcher.group(2) ?: ""
            val start = matcher.start()
            val end = matcher.end()
            builder.replace(start, end, captured)
            builder.setSpan(
                createSpan(),
                start,
                start + captured.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            matcher = pattern.matcher(builder)
        }
    }

    private fun applyCodeSpans(builder: SpannableStringBuilder) {
        var matcher = CODE_PATTERN.matcher(builder)
        while (matcher.find()) {
            val code = matcher.group(1) ?: ""
            val start = matcher.start()
            val end = matcher.end()
            builder.replace(start, end, code)
            val targetEnd = start + code.length
            builder.setSpan(
                TypefaceSpan("monospace"),
                start,
                targetEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                BackgroundColorSpan(0xFF202532.toInt()),
                start,
                targetEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            matcher = CODE_PATTERN.matcher(builder)
        }
    }

    private fun applyLinks(builder: SpannableStringBuilder) {
        var matcher = LINK_PATTERN.matcher(builder)
        while (matcher.find()) {
            val title = matcher.group(1) ?: ""
            val url = matcher.group(2) ?: ""
            val start = matcher.start()
            val end = matcher.end()
            builder.replace(start, end, title)
            val targetEnd = start + title.length
            builder.setSpan(
                URLSpan(url),
                start,
                targetEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                ForegroundColorSpan(0xFFD0BCFF.toInt()),
                start,
                targetEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            matcher = LINK_PATTERN.matcher(builder)
        }
    }

    private fun applyBullets(builder: SpannableStringBuilder) {
        var matcher = BULLET_LINE.matcher(builder)
        while (matcher.find()) {
            val content = matcher.group(1) ?: ""
            val start = matcher.start()
            val end = matcher.end()
            val bulletText = "•  $content"
            builder.replace(start, end, bulletText)
            matcher = BULLET_LINE.matcher(builder)
        }
    }
}
