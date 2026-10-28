package com.gathin.tvhome.feature.agent.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.feature.agent.coordinator.DuinchBrain
import com.gathin.tvhome.feature.agent.widget.AgentAnswerView
import com.gathin.tvhome.feature.agent.widget.DuinchView
import com.gathin.tvhome.feature.main.widget.TypingGreetingView

class AgentPanelViews(private val context: Context, private val actions: Actions) {

    interface Actions {
        fun typeRequested()
        fun speakRequested()
        fun voiceToggled()
        fun viewerToggled()
        fun suggestionChosen(text: String)
        fun typed(text: String)
    }

    val bot = DuinchView(context)
    val line = TypingGreetingView(context)
    val answer = AgentAnswerView(context)
    val input = EditText(context)
    val chips = HorizontalScrollView(context)
    val column = LinearLayout(context)

    private val chipsRow = LinearLayout(context)
    private lateinit var speakChip: TextView
    private lateinit var voiceChip: TextView
    private lateinit var viewerChip: TextView

    fun build(speakReplies: Boolean): LinearLayout {
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(0, dp(26), 0, 0)
        column.isFocusable = true
        column.isFocusableInTouchMode = true
        column.addView(buildTopRow())
        buildChips(speakReplies)
        column.addView(chips, LinearLayout.LayoutParams(MATCH, WRAP))
        column.addView(answer, LinearLayout.LayoutParams(MATCH, 0, 1f))
        answer.onResultsShown = { shown -> chips.visibility = if (shown) View.GONE else View.VISIBLE }
        answer.onLeaveResults = {
            chips.visibility = View.VISIBLE
            speakChip.requestFocus()
        }
        return column
    }

    fun isTyping(): Boolean = input.visibility == View.VISIBLE

    fun showInput() {
        chips.visibility = View.GONE
        line.visibility = View.GONE
        input.visibility = View.VISIBLE
        input.requestFocus()
        keyboard()?.showSoftInput(input, 0)
    }

    fun hideInput() {
        input.setText("")
        input.visibility = View.GONE
        line.visibility = View.VISIBLE
        keyboard()?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        column.requestFocus()
    }

    fun leaveInput() {
        hideInput()
        chips.visibility = View.VISIBLE
        focusSpeak()
    }

    fun resetLine() {
        line.animate()?.cancel()
        line.alpha = 1f
        line.setTextColor(TEXT_COLOR)
        line.text = ""
    }

    fun fadeLine(message: CharSequence, color: Int) {
        line.animate().cancel()
        line.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            showLine(message, color)
        }.start()
    }

    fun showChips(wanted: Boolean) {
        chips.visibility = if (wanted && !answer.hasResults) View.VISIBLE else View.GONE
    }

    fun focusSpeak() {
        speakChip.requestFocus()
    }

    fun hasChipFocus(): Boolean = chips.hasFocus()

    fun setVoiceLabel(speakReplies: Boolean) {
        voiceChip.text = voiceLabel(speakReplies)
    }

    fun setViewerLabel() {
        viewerChip.text = viewerLabel()
    }

    fun populateSuggestions() {
        while (chipsRow.childCount > FIXED_CHIPS) chipsRow.removeViewAt(FIXED_CHIPS)
        for (suggestion in DuinchBrain.suggestions(context)) {
            chipsRow.addView(chip(suggestion, 0) { actions.suggestionChosen(suggestion) })
        }
    }

    fun showLine(message: CharSequence, color: Int) {
        line.animate().cancel()
        line.alpha = 1f
        line.setTextColor(color)
        line.showMessage(message, animate = false)
    }

    private fun buildTopRow(): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.minimumHeight = dp(48)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(CONTENT_PADDING_DP), 0, dp(CONTENT_PADDING_DP), 0)
        row.addView(bot, LinearLayout.LayoutParams(dp(54), dp(42)))

        val textSlot = FrameLayout(context)
        textSlot.setPadding(dp(LINE_PADDING_DP), 0, 0, 0)
        styleLine()
        textSlot.addView(line, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL))
        styleInput()
        textSlot.addView(input, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL))
        row.addView(textSlot, LinearLayout.LayoutParams(0, WRAP, 1f))
        return row
    }

    private fun styleLine() {
        line.setTextSize(TypedValue.COMPLEX_UNIT_SP, LINE_SIZE_SP.toFloat())
        line.typeface = Fonts.MEDIUM
        line.letterSpacing = 0.01f
        line.setTextColor(TEXT_COLOR)
        line.maxLines = 2
        line.setBot(bot)
    }

    private fun styleInput() {
        input.setHint(R.string.agent_type_hint)
        input.setTextColor(TEXT_COLOR)
        input.setHintTextColor(HINT_COLOR)
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, LINE_SIZE_SP.toFloat())
        input.typeface = Fonts.MEDIUM
        input.background = null
        input.setPadding(0, 0, 0, 0)
        input.isSingleLine = true
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.visibility = View.GONE
        input.setOnEditorActionListener { field, _, _ ->
            val typed = field.text.toString().trim()
            field.setText("")
            if (typed.isNotEmpty()) actions.typed(typed)
            true
        }
    }

    private fun buildChips(speakReplies: Boolean) {
        chipsRow.orientation = LinearLayout.HORIZONTAL
        chipsRow.setPadding(dp(CONTENT_PADDING_DP), dp(16), dp(CONTENT_PADDING_DP), dp(8))
        chipsRow.addView(chip(null, R.drawable.ic_keyboard) { actions.typeRequested() })
        speakChip = chip(context.getString(R.string.agent_chip_speak), 0) {
            actions.speakRequested()
        }
        chipsRow.addView(speakChip)
        voiceChip = chip(voiceLabel(speakReplies), 0) { actions.voiceToggled() }
        chipsRow.addView(voiceChip)
        viewerChip = chip(viewerLabel(), 0) { actions.viewerToggled() }
        chipsRow.addView(viewerChip)
        populateSuggestions()
        chips.isHorizontalScrollBarEnabled = false
        chips.addView(chipsRow)
    }

    private fun keyboard(): InputMethodManager? {
        return context.getSystemService(InputMethodManager::class.java)
    }

    private fun voiceLabel(speakReplies: Boolean): String {
        val label = if (speakReplies) R.string.agent_voice_on else R.string.agent_voice_off
        return context.getString(label)
    }

    private fun viewerLabel(): String {
        val profile = DuinchBrain.viewer(context)
        return String.format(
            context.getString(R.string.duinch_chip_viewer),
            DuinchBrain.viewerLabel(context, profile)
        )
    }

    private fun chip(label: String?, icon: Int, onClick: Runnable): TextView {
        val chip = TextView(context)
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, CHIP_SIZE_SP.toFloat())
        chip.gravity = Gravity.CENTER
        chip.isSingleLine = true
        val sidePadding = dp(if (label == null) 12 else 20)
        chip.setPadding(sidePadding, dp(8), sidePadding, dp(8))
        if (label != null) chip.text = label
        if (icon != 0) chip.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        chip.isFocusable = true
        chip.isClickable = true
        chip.setOnClickListener { onClick.run() }
        paintChip(chip, false)
        chip.setOnFocusChangeListener { _, focused -> paintChip(chip, focused) }
        val params = LinearLayout.LayoutParams(WRAP, WRAP)
        params.rightMargin = dp(10)
        chip.layoutParams = params
        return chip
    }

    private fun paintChip(chip: TextView, focused: Boolean) {
        val background = GradientDrawable()
        background.cornerRadius = dp(40).toFloat()
        background.setColor(if (focused) CHIP_FOCUSED_COLOR else CHIP_COLOR)
        chip.background = background
        val foreground = if (focused) CHIP_FOCUSED_TEXT_COLOR else TEXT_COLOR
        chip.setTextColor(foreground)
        for (icon in chip.compoundDrawables) {
            icon?.setColorFilter(foreground, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun dp(value: Int): Int = Math.round(value * context.resources.displayMetrics.density)

    companion object {
        const val BOT_ACCENT = 0xFF00E5FF.toInt()
        const val TEXT_COLOR = 0xFFE6E6E6.toInt()
        private val SCRIM_COLORS = intArrayOf(
            0xF5080B10.toInt(), 0xE6080B10.toInt(), 0xCC080B10.toInt()
        )

        fun scrim(): GradientDrawable =
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, SCRIM_COLORS)
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val FADE_MS = 160L
        private const val FIXED_CHIPS = 4
        private const val HINT_COLOR = 0xFF7A838C.toInt()
        private const val CHIP_COLOR = 0xFF262B33.toInt()
        private const val CHIP_FOCUSED_COLOR = 0xFFE6E1EC.toInt()
        private const val CHIP_FOCUSED_TEXT_COLOR = 0xFF1B1B1F.toInt()
        private const val CONTENT_PADDING_DP = 56
        private const val LINE_PADDING_DP = 12
        private const val LINE_SIZE_SP = 20
        private const val CHIP_SIZE_SP = 15
    }
}
