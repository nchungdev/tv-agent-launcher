package com.gathin.tvhome.feature.main.widget

import com.gathin.tvhome.feature.agent.coordinator.DuinchBrain
import com.gathin.tvhome.feature.agent.widget.DuinchView
import android.annotation.SuppressLint
import android.content.Context
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import java.text.BreakIterator
import java.util.Locale
import java.util.regex.Pattern



class TypingGreetingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    private val density = resources.displayMetrics.density
    private var targetBot: DuinchView? = null
    private var attachedGrid: RecyclerView? = null
    private var message = ""
    private var revealed = 0
    private var isThinking = false
    private var pages: List<String> = emptyList()
    private var pageIndex = 0
    private var maxExpandedLines = DEFAULT_MAX_LINES
    private var currentVisibleLines = DEFAULT_MAX_LINES
    private var activeTextSizeSp = MAX_TEXT_SIZE_SP

    private val greetingPos = IntArray(2)
    private val calendarPos = IntArray(2)
    private val headerPos = IntArray(2)
    private val characters = BreakIterator.getCharacterInstance()

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        syncHierarchyAndBounds()
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            updateDynamicLineBounds()
        }
    }

    init {
        setTextColor(0xFFF1F5F9.toInt())
        includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
        maxLines = DEFAULT_MAX_LINES
        minHeight = (DEFAULT_BOT_HEIGHT_DP * density).toInt()
        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
    }

    private val startTypingTask = Runnable {
        isThinking = false
        startPage(0)
    }

    private val nextPageTask = Runnable {
        if (!isShown || windowVisibility != VISIBLE) return@Runnable
        if (pageIndex + 1 < pages.size) {
            text = ""
            startPage(pageIndex + 1)
        }
    }

    private val typeNext = object : Runnable {
        override fun run() {
            if (!isShown || windowVisibility != VISIBLE) return
            val currentPage = pages.getOrNull(pageIndex) ?: return
            val next = characters.next()
            if (next == BreakIterator.DONE) {
                finishPage()
                return
            }
            revealed = next
            text = formatGreeting(currentPage.substring(0, revealed) + CURSOR_CHAR)
            if (revealed < currentPage.length) {
                targetBot?.setTalking(true)
                val delay = characterDelay(currentPage[next - 1])
                postDelayed(this, delay)
            } else {
                finishPage()
            }
        }
    }

    fun setBot(bot: DuinchView?) {
        targetBot = bot
        syncBotAlignment()
    }

    private fun syncHierarchyAndBounds() {
        syncBotAlignment()
        unclipParentChain()
        attachGridScrollListenerIfNeeded()
        updateDynamicLineBounds()
    }

    private fun syncBotAlignment() {
        val bot = targetBot ?: rootView?.findViewById(R.id.duinchBot)
        if (bot != null) {
            targetBot = bot
            val botHeight = if (bot.height > 0) {
                bot.height
            } else {
                bot.layoutParams?.height ?: 0
            }
            if (botHeight > 0 && minHeight != botHeight) {
                minHeight = botHeight
            }
            val wrapper = parent as? ViewGroup
            val wrapperLp = wrapper?.layoutParams as? ViewGroup.MarginLayoutParams
            val botLp = bot.layoutParams as? ViewGroup.MarginLayoutParams
            if (wrapper != null && wrapperLp != null) {
                var changed = false
                if (wrapperLp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                    wrapperLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    changed = true
                }
                if (botLp != null && wrapperLp.topMargin != botLp.topMargin) {
                    wrapperLp.topMargin = botLp.topMargin
                    changed = true
                }
                if (changed) {
                    wrapper.layoutParams = wrapperLp
                }
            }
        }
    }

    private fun unclipParentChain() {
        var current = parent
        while (current is ViewGroup) {
            if (current.clipChildren) current.clipChildren = false
            if (current.clipToPadding) current.clipToPadding = false
            if (current.id == R.id.headerRow) break
            current = current.parent
        }
    }

    private fun attachGridScrollListenerIfNeeded() {
        val gridView = rootView?.findViewById<RecyclerView>(R.id.grid) ?: return
        if (attachedGrid !== gridView) {
            attachedGrid?.removeOnScrollListener(scrollListener)
            attachedGrid = gridView
            gridView.addOnScrollListener(scrollListener)
        }
    }

    private fun updateDynamicLineBounds() {
        if (!isShown || lineHeight <= 0) return
        val root = rootView ?: return
        getLocationInWindow(greetingPos)
        val greetingTop = greetingPos[1]
        if (greetingTop <= 0) return

        val safeGap = (CALENDAR_SAFE_GAP_DP * density).toInt()
        val calendarView = root.findViewById<View>(R.id.widgetClock)
        var expanded = DEFAULT_MAX_LINES
        var currentCalendarVisualTop = Int.MAX_VALUE

        if (calendarView != null && calendarView.width > 0) {
            calendarView.getLocationInWindow(calendarPos)
            val shelfTranslationY = (calendarView.parent as? View)?.translationY?.toInt() ?: 0
            val restingVisualTop = calendarPos[1] - shelfTranslationY + calendarView.paddingTop
            currentCalendarVisualTop = calendarPos[1] + calendarView.paddingTop
            val restingAvailable = restingVisualTop - safeGap - greetingTop
            if (restingAvailable > 0) {
                val baseLineHeight = (DEFAULT_LINE_HEIGHT_DP * density).toInt().coerceAtLeast(1)
                expanded = (restingAvailable / baseLineHeight)
                    .coerceIn(MIN_EXPANDED_LINES, MAX_EXPANDED_LINES)
            }
        }

        val scrollOffset = attachedGrid?.computeVerticalScrollOffset() ?: 0
        val scrollThreshold = (SCROLL_SHRINK_THRESHOLD_DP * density).toInt()
        val visible = if (scrollOffset <= scrollThreshold) {
            expanded
        } else {
            val headerRow = root.findViewById<View>(R.id.headerRow)
            val headerBottom = if (headerRow != null && headerRow.height > 0) {
                headerRow.getLocationInWindow(headerPos)
                headerPos[1] + headerRow.height
            } else {
                greetingTop + lineHeight * COLLAPSED_SCROLL_LINES
            }
            val shelfView = calendarView?.parent as? View
            val calendarActive = calendarView != null &&
                calendarView.isShown &&
                shelfView?.visibility == View.VISIBLE &&
                (shelfView.alpha > 0.25f)
            val limitBottom = if (calendarActive) {
                minOf(headerBottom, currentCalendarVisualTop - safeGap / 2)
            } else {
                headerBottom
            }
            val scrolledAvailable = limitBottom - greetingTop
            (scrolledAvailable / lineHeight).coerceIn(1, COLLAPSED_SCROLL_LINES)
        }

        setDynamicLineBounds(expanded, visible)
    }

    fun setDynamicLineBounds(expandedLines: Int, visibleLines: Int) {
        val targetExpanded = expandedLines.coerceIn(MIN_EXPANDED_LINES, MAX_EXPANDED_LINES)
        val targetVisible = visibleLines.coerceIn(1, targetExpanded)
        val expandedChanged = targetExpanded != maxExpandedLines
        maxExpandedLines = targetExpanded
        if (expandedChanged && message.isNotEmpty() && isShown) {
            val availableWidth = resolveAvailableWidth()
            val updatedPages = computePages(availableWidth)
            if (updatedPages != pages) {
                val activePage = pages.getOrNull(pageIndex) ?: ""
                val typingActive = isThinking || (revealed in 1 until activePage.length)
                if (typingActive) {
                    stopTyping()
                    pages = updatedPages
                    startPage(0)
                } else {
                    pages = updatedPages
                    pageIndex = 0
                    val firstPage = pages.firstOrNull() ?: message
                    revealed = firstPage.length
                    applyOptimalTextSize(firstPage, availableWidth)
                    applyLineGravity(firstPage)
                    text = formatGreeting(firstPage)
                }
            }
        }
        if (targetVisible != currentVisibleLines || maxLines != targetVisible) {
            currentVisibleLines = targetVisible
            maxLines = targetVisible
            ellipsize = TextUtils.TruncateAt.END
            val currentPage = pages.getOrNull(pageIndex) ?: message
            applyLineGravity(currentPage)
        }
    }

    fun showMessage(
        value: CharSequence,
        animate: Boolean = true,
        simulateThinking: Boolean = true
    ) {
        val next = value.toString().trim()
        if (next == message && pages.isNotEmpty() && !isThinking) {
            val currentPage = pages.getOrNull(pageIndex) ?: ""
            if (revealed < currentPage.length) {
                removeCallbacks(typeNext)
                targetBot?.setTalking(true)
                post(typeNext)
            }
            return
        }
        stopTyping()
        message = next
        contentDescription = message

        val availableWidth = resolveAvailableWidth()
        if (!animate || message.isEmpty()) {
            applyOptimalTextSize(message, availableWidth)
            val prepared = preventOrphanWordWrap(message, availableWidth)
            pages = listOf(prepared)
            pageIndex = 0
            revealed = prepared.length
            applyLineGravity(prepared)
            text = formatGreeting(prepared)
            return
        }

        preparePages(availableWidth)
        pageIndex = 0
        revealed = 0
        text = ""

        if (simulateThinking && targetBot != null) {
            isThinking = true
            targetBot?.setMood(DuinchView.THINKING)
            postDelayed(startTypingTask, THINKING_DELAY_MS)
        } else {
            startPage(0)
        }
    }

    fun stopTyping() {
        removeCallbacks(startTypingTask)
        removeCallbacks(typeNext)
        removeCallbacks(nextPageTask)
        if (isThinking && targetBot?.getMood() == DuinchView.THINKING) {
            targetBot?.setMood(DuinchView.IDLE)
        }
        isThinking = false
        targetBot?.setTalking(false)
        val currentPage = pages.getOrNull(pageIndex)
        if (currentPage != null && revealed in 1 until currentPage.length) {
            revealed = currentPage.length
            text = formatGreeting(currentPage)
        }
    }

    private fun applyOptimalTextSize(content: String, availableWidth: Int) {
        if (content.isEmpty() || availableWidth <= 1) {
            setTextSizeIfNeeded(MAX_TEXT_SIZE_SP)
            return
        }
        val measuredWithCursor = "$content$CURSOR_CHAR"
        var bestSp = MAX_TEXT_SIZE_SP
        var foundIdeal = false
        var foundFitting = false

        var candidateSp = MAX_TEXT_SIZE_SP
        while (candidateSp >= MIN_TEXT_SIZE_SP) {
            val layoutClean = buildStaticLayout(content, availableWidth, candidateSp)
            val layoutCursor = buildStaticLayout(measuredWithCursor, availableWidth, candidateSp)
            val lines = maxOf(layoutClean.lineCount, layoutCursor.lineCount)
            val fitsHeight = lines <= maxExpandedLines
            val sameLineCount = layoutClean.lineCount == layoutCursor.lineCount
            val hasOrphan = hasOrphanLastWord(layoutClean, content, availableWidth) ||
                hasOrphanLastWord(layoutCursor, content, availableWidth)
            if (fitsHeight && sameLineCount && !hasOrphan) {
                bestSp = candidateSp
                foundIdeal = true
                break
            }
            if (fitsHeight && !foundFitting) {
                bestSp = candidateSp
                foundFitting = true
            }
            candidateSp -= TEXT_SIZE_STEP_SP
        }

        if (!foundIdeal && !foundFitting) {
            bestSp = MIN_TEXT_SIZE_SP
        }
        setTextSizeIfNeeded(bestSp)
    }

    private fun setTextSizeIfNeeded(sizeSp: Float) {
        if (Math.abs(activeTextSizeSp - sizeSp) > 0.1f) {
            activeTextSizeSp = sizeSp
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }
    }

    private fun hasOrphanLastWord(
        layout: StaticLayout,
        rawContent: String,
        availableWidth: Int
    ): Boolean {
        val lines = layout.lineCount
        if (lines < 2) return false
        val start = layout.getLineStart(lines - 1).coerceAtMost(rawContent.length)
        val end = layout.getLineEnd(lines - 1).coerceAtMost(rawContent.length)
        if (start >= end) return true
        val lastLine = rawContent.substring(start, end).trim()
        val words = lastLine.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lineWidth = layout.getLineWidth(lines - 1)
        if (words.size <= 1) return true
        return words.size <= 2 && lineWidth < availableWidth * ORPHAN_LINE_WIDTH_RATIO
    }

    private fun preventOrphanWordWrap(content: String, availableWidth: Int): String {
        if (content.isEmpty() || availableWidth <= 1) return content
        val layoutClean = buildStaticLayout(content, availableWidth, activeTextSizeSp)
        val layoutCursor = buildStaticLayout(
            "$content$CURSOR_CHAR",
            availableWidth,
            activeTextSizeSp
        )
        if (!hasOrphanLastWord(layoutClean, content, availableWidth) &&
            !hasOrphanLastWord(layoutCursor, content, availableWidth)
        ) {
            return content
        }
        val lastSpace = content.lastIndexOf(' ')
        if (lastSpace <= 0) return content
        return content.substring(0, lastSpace) + "\u00A0" + content.substring(lastSpace + 1)
    }

    private fun formatGreeting(content: String): CharSequence {
        if (content.isEmpty()) return content
        val builder = SpannableStringBuilder(content)
        val targets = mutableListOf("Duinch")
        val customName = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
            .getString(Config.KEY_GREETING_NAME, null)?.trim()
        if (!customName.isNullOrEmpty()) targets.add(customName)
        for (i in 0 until DuinchBrain.VIEWER_COUNT) {
            val label = DuinchBrain.viewerLabel(context, i)
            if (label.isNotEmpty()) targets.add(label)
        }
        val cyan = 0xFF38BDF8.toInt()
        for (target in targets) {
            var start = 0
            while (start < content.length) {
                val idx = content.indexOf(target, start, ignoreCase = true)
                if (idx == -1) break
                val end = (idx + target.length).coerceAtMost(content.length)
                builder.setSpan(
                    ForegroundColorSpan(cyan),
                    idx,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                start = end
            }
        }
        return builder
    }

    private fun resolveAvailableWidth(): Int {
        val horizontalPadding = paddingLeft + paddingRight
        val screenWidth = resources.displayMetrics.widthPixels
        if (maxWidth in (horizontalPadding + 1)..screenWidth) {
            return maxWidth - horizontalPadding
        }
        if (width > horizontalPadding) return width - horizontalPadding
        val screenHalf = screenWidth / 2
        return (screenHalf - horizontalPadding).coerceAtLeast(100)
    }

    private fun startPage(index: Int) {
        pageIndex = index
        revealed = 0
        val rawPage = pages.getOrNull(pageIndex) ?: return
        val availableWidth = resolveAvailableWidth()
        applyOptimalTextSize(rawPage, availableWidth)
        val page = preventOrphanWordWrap(rawPage, availableWidth)
        if (page != rawPage) {
            val mutable = pages.toMutableList()
            mutable[pageIndex] = page
            pages = mutable
        }
        applyLineGravity(page)
        characters.setText(page)
        targetBot?.setTalking(true)
        if (targetBot?.getMood() == DuinchView.THINKING) {
            targetBot?.setMood(DuinchView.HAPPY)
        }
        post(typeNext)
    }

    private fun applyLineGravity(content: String) {
        val availableWidth = resolveAvailableWidth()
        val rawLines = if (content.isNotEmpty() && availableWidth > 1) {
            measureLineCount(content, availableWidth, activeTextSizeSp)
        } else {
            1
        }
        val lines = minOf(currentVisibleLines, rawLines)
        val desiredGravity = if (lines >= 2) {
            Gravity.LEFT or Gravity.TOP
        } else {
            Gravity.LEFT or Gravity.CENTER_VERTICAL
        }
        if (gravity != desiredGravity) {
            gravity = desiredGravity
        }
    }

    private fun finishPage() {
        val currentPage = pages.getOrNull(pageIndex) ?: return
        text = formatGreeting(currentPage)
        targetBot?.setTalking(false)
        if (pageIndex < pages.size - 1) {
            val pauseMs = maxOf(MIN_PAGE_PAUSE_MS, currentPage.length * CHAR_READ_PAUSE_MS)
            postDelayed(nextPageTask, pauseMs)
        } else {
            if (targetBot?.getMood() == DuinchView.THINKING ||
                targetBot?.getMood() == DuinchView.HAPPY
            ) {
                targetBot?.setMood(DuinchView.IDLE)
            }
        }
    }

    private fun preparePages(availableWidth: Int) {
        pages = computePages(availableWidth)
    }

    private fun computePages(availableWidth: Int): List<String> {
        val minSizeLines = measureLineCount(
            "$message$CURSOR_CHAR",
            availableWidth,
            MIN_TEXT_SIZE_SP
        )
        if (minSizeLines <= maxExpandedLines) {
            return listOf(message)
        }
        val rawPages = rebalanceChunks(splitIntoSentences(message, availableWidth), availableWidth)
        return rawPages.mapIndexed { idx, page ->
            normalizePageClause(page, idx > 0)
        }.filter { it.isNotEmpty() }
    }

    private fun splitIntoSentences(text: String, availableWidth: Int): List<String> {
        val rawSentences = mutableListOf<String>()
        val matcher = SENTENCE_PATTERN.matcher(text)
        while (matcher.find()) {
            val sentence = matcher.group().trim()
            if (sentence.isNotEmpty()) {
                rawSentences.add(sentence)
            }
        }
        if (rawSentences.isEmpty()) rawSentences.add(text)

        val result = mutableListOf<String>()
        var currentBlock = ""
        for (sentence in rawSentences) {
            val candidate = if (currentBlock.isEmpty()) sentence else "$currentBlock $sentence"
            if (measureLineCount(candidate, availableWidth, MIN_TEXT_SIZE_SP) <= maxExpandedLines) {
                currentBlock = candidate
            } else {
                if (currentBlock.isNotEmpty()) {
                    result.add(currentBlock)
                    currentBlock = ""
                }
                if (measureLineCount(
                        sentence,
                        availableWidth,
                        MIN_TEXT_SIZE_SP
                    ) <= maxExpandedLines
                ) {
                    currentBlock = sentence
                } else {
                    result.addAll(splitByClausesAndWords(sentence, availableWidth))
                }
            }
        }
        if (currentBlock.isNotEmpty()) {
            result.add(currentBlock)
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    private fun splitByClausesAndWords(sentence: String, availableWidth: Int): List<String> {
        val clauses = sentence.split(CLAUSE_SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
        if (clauses.size <= 1) {
            return chunkLongClause(sentence, availableWidth)
        }
        val result = mutableListOf<String>()
        var current = ""
        for (clause in clauses) {
            val candidate = if (current.isEmpty()) clause else "$current $clause"
            if (measureLineCount(candidate, availableWidth, MIN_TEXT_SIZE_SP) <= maxExpandedLines) {
                current = candidate
            } else {
                if (current.isNotEmpty()) {
                    result.add(current)
                    current = ""
                }
                if (measureLineCount(clause, availableWidth, MIN_TEXT_SIZE_SP) <= maxExpandedLines) {
                    current = clause
                } else {
                    result.addAll(chunkLongClause(clause, availableWidth))
                }
            }
        }
        if (current.isNotEmpty()) {
            result.add(current)
        }
        return rebalanceChunks(result, availableWidth)
    }

    private fun chunkLongClause(clause: String, availableWidth: Int): List<String> {
        val words = clause.split(" ").filter { it.isNotEmpty() }
        if (words.size <= MIN_LAST_CHUNK_WORDS * 2) {
            return listOf(clause)
        }
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (measureLineCount(candidate, availableWidth, MIN_TEXT_SIZE_SP) <= maxExpandedLines) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) chunks.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return rebalanceChunks(chunks, availableWidth)
    }

    private fun rebalanceChunks(chunks: List<String>, availableWidth: Int): List<String> {
        if (chunks.size < 2) return chunks
        val balanced = chunks.toMutableList()
        val prevIndex = balanced.lastIndex - 1
        val lastIndex = balanced.lastIndex
        val merged = "${balanced[prevIndex]} ${balanced[lastIndex]}".trim()
        if (measureLineCount(merged, availableWidth, MIN_TEXT_SIZE_SP) <= maxExpandedLines) {
            balanced[prevIndex] = merged
            balanced.removeAt(lastIndex)
            return balanced
        }
        val prevWords = balanced[prevIndex].split(" ").filter { it.isNotEmpty() }.toMutableList()
        val lastWords = balanced[lastIndex].split(" ").filter { it.isNotEmpty() }.toMutableList()
        while (
            (lastWords.size < MIN_LAST_CHUNK_WORDS || isLeadingConnector(lastWords.firstOrNull())) &&
            prevWords.size > MIN_LAST_CHUNK_WORDS
        ) {
            val movedWord = prevWords.last()
            val candidateLast = (listOf(movedWord) + lastWords).joinToString(" ")
            if (measureLineCount(
                    candidateLast,
                    availableWidth,
                    MIN_TEXT_SIZE_SP
                ) <= maxExpandedLines
            ) {
                prevWords.removeAt(prevWords.lastIndex)
                lastWords.add(0, movedWord)
            } else {
                break
            }
        }
        balanced[prevIndex] = prevWords.joinToString(" ")
        balanced[lastIndex] = lastWords.joinToString(" ")
        return balanced
    }

    private fun isLeadingConnector(word: String?): Boolean {
        if (word.isNullOrEmpty()) return false
        val cleaned = word.lowercase(Locale.ROOT).trim(',', ';', ':', '.', '!', '?')
        return cleaned in LEADING_CONNECTORS
    }

    private fun normalizePageClause(rawPage: String, isContinuation: Boolean): String {
        var text = rawPage.trim().trimStart(',', ';', ':', '-', '–')
            .trim()
        if (isContinuation) {
            text = LEADING_CONJUNCTION_REGEX.replaceFirst(text, "").trim()
        }
        if (text.isEmpty()) return ""
        val firstChar = text[0]
        if (firstChar.isLowerCase()) {
            text = firstChar.uppercaseChar() + text.substring(1)
        }
        return text
    }

    private fun measureLineCount(
        content: String,
        availableWidth: Int,
        sizeSp: Float = activeTextSizeSp
    ): Int {
        if (content.isEmpty() || availableWidth <= 1) return 0
        return buildStaticLayout(content, availableWidth, sizeSp).lineCount
    }

    private fun buildStaticLayout(
        content: String,
        availableWidth: Int,
        sizeSp: Float
    ): StaticLayout {
        val textPaint = TextPaint(paint)
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sizeSp,
            resources.displayMetrics
        )
        return StaticLayout.Builder.obtain(
            content,
            0,
            content.length,
            textPaint,
            availableWidth.coerceAtLeast(1)
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(includeFontPadding)
            .setBreakStrategy(breakStrategy)
            .setHyphenationFrequency(hyphenationFrequency)
            .build()
    }

    private fun characterDelay(char: Char): Long {
        return when (char) {
            ',', ';', ':' -> PUNCTUATION_DELAY_MS
            '.', '!', '?', '\n' -> SENTENCE_DELAY_MS
            ' ' -> SPACE_DELAY_MS
            else -> DEFAULT_CHAR_DELAY_MS
        }
    }

    override fun setMaxWidth(maxPixels: Int) {
        val changed = maxPixels != maxWidth
        super.setMaxWidth(maxPixels)
        if (changed && message.isNotEmpty() && isShown) {
            val availableWidth = resolveAvailableWidth()
            val updatedPages = computePages(availableWidth)
            if (updatedPages != pages) {
                stopTyping()
                pages = updatedPages
                startPage(0)
            } else {
                val currentPage = pages.getOrNull(pageIndex) ?: message
                applyOptimalTextSize(currentPage, availableWidth)
                applyLineGravity(currentPage)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        syncBotAlignment()
        measureConstrained(widthMeasureSpec, heightMeasureSpec)
        val widthForLines = (measuredWidth - paddingLeft - paddingRight)
            .coerceAtLeast(resolveAvailableWidth())
        val currentPage = pages.getOrNull(pageIndex) ?: message
        val expectedLines = if (currentPage.isNotEmpty() && widthForLines > 1) {
            measureLineCount(currentPage, widthForLines, activeTextSizeSp)
        } else {
            lineCount
        }
        val effectiveLines = minOf(currentVisibleLines, maxOf(lineCount, expectedLines))
        val desiredGravity = if (effectiveLines >= 2) {
            Gravity.LEFT or Gravity.TOP
        } else {
            Gravity.LEFT or Gravity.CENTER_VERTICAL
        }
        if (gravity != desiredGravity) {
            gravity = desiredGravity
            measureConstrained(widthMeasureSpec, heightMeasureSpec)
        }
    }

    @SuppressLint("WrongCall")
    private fun measureConstrained(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = maxWidth
        if (maxW > 0) {
            val parentSize = MeasureSpec.getSize(widthMeasureSpec)
            val parentMode = MeasureSpec.getMode(widthMeasureSpec)
            if (parentMode == MeasureSpec.EXACTLY && parentSize > maxW) {
                val constrainedSpec = MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.EXACTLY)
                super.onMeasure(constrainedSpec, heightMeasureSpec)
                return
            } else if (parentSize > maxW || parentMode == MeasureSpec.UNSPECIFIED) {
                val constrainedSpec = MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST)
                super.onMeasure(constrainedSpec, heightMeasureSpec)
                return
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        post { syncHierarchyAndBounds() }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            stopTyping()
        } else {
            post { syncHierarchyAndBounds() }
            val currentPage = pages.getOrNull(pageIndex) ?: ""
            if (revealed < currentPage.length) {
                removeCallbacks(typeNext)
                post(typeNext)
            }
        }
    }

    override fun onDetachedFromWindow() {
        stopTyping()
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        }
        attachedGrid?.removeOnScrollListener(scrollListener)
        attachedGrid = null
        super.onDetachedFromWindow()
    }

    companion object {
        private const val DEFAULT_BOT_HEIGHT_DP = 42
        private const val DEFAULT_LINE_HEIGHT_DP = 24
        private const val DEFAULT_MAX_LINES = 5
        private const val MIN_EXPANDED_LINES = 3
        private const val MAX_EXPANDED_LINES = 5
        private const val COLLAPSED_SCROLL_LINES = 2
        private const val CALENDAR_SAFE_GAP_DP = 16
        private const val SCROLL_SHRINK_THRESHOLD_DP = 6
        private const val MIN_LAST_CHUNK_WORDS = 4
        private const val MAX_TEXT_SIZE_SP = 20f
        private const val MIN_TEXT_SIZE_SP = 14.5f
        private const val TEXT_SIZE_STEP_SP = 0.5f
        private const val ORPHAN_LINE_WIDTH_RATIO = 0.28f
        private const val DEFAULT_CHAR_DELAY_MS = 38L
        private const val SPACE_DELAY_MS = 55L
        private const val PUNCTUATION_DELAY_MS = 140L
        private const val SENTENCE_DELAY_MS = 280L
        private const val THINKING_DELAY_MS = 250L
        private const val MIN_PAGE_PAUSE_MS = 6000L
        private const val CHAR_READ_PAUSE_MS = 75L
        private const val CURSOR_CHAR = " ▍"
        private val SENTENCE_PATTERN = Pattern.compile("[^.!?…\\n]+[.!?…]+|[^.!?…\\n]+$")
        private val CLAUSE_SPLIT_REGEX = Regex("(?<=[,;:])\\s+")
        private val LEADING_CONJUNCTION_REGEX = Regex(
            "^(hoặc là|hay là|hoặc|hay|và|nhưng mà|nhưng|mà|rồi|để)\\s+",
            RegexOption.IGNORE_CASE
        )
        private val LEADING_CONNECTORS = setOf(
            "hoặc", "hay", "và", "nhưng", "mà", "rồi", "để",
            "với", "cùng", "nhé", "nha", "nhỉ", "ạ", "nè", "đó"
        )
    }
}
