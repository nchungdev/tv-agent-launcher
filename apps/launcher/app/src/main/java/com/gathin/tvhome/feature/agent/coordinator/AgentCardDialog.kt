package com.gathin.tvhome.feature.agent.coordinator

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.Shapes

object AgentCardDialog {

    class Button(val label: String, val onClick: () -> Unit)

    fun show(context: Context, title: String, message: String, buttons: List<Button>) {
        val density = context.resources.displayMetrics.density
        val palette = Palette.currentOrBaseline()
        val panel = panel(context, density, palette.surfaceContainerHigh)
        panel.addView(text(context, title, TITLE_SP, palette.onSurface, Fonts.MEDIUM))
        panel.addView(body(context, message, density, palette.onSurfaceVariant))

        val dialog = Dialog(context)
        val row = buttonRow(context, density)
        val views = buttons.mapIndexed { index, button ->
            buttonView(context, button.label, density, index).also { view ->
                view.setOnClickListener {
                    dialog.dismiss()
                    button.onClick()
                }
                row.addView(view)
            }
        }
        panel.addView(row)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(panel)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(0))
            window.setLayout((WIDTH_DP * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(DIM)
        }
        M3Skin.apply(panel, palette, true, false)
        dialog.show()
        views.lastOrNull()?.requestFocus()
    }

    private fun panel(context: Context, density: Float, color: Int): LinearLayout {
        val panel = LinearLayout(context)
        panel.orientation = LinearLayout.VERTICAL
        val padding = (PADDING_DP * density).toInt()
        panel.setPadding(padding, padding, padding, padding)
        panel.background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = Shapes.EXTRA_LARGE * density
        }
        panel.elevation = ELEVATION_DP * density
        return panel
    }

    private fun body(context: Context, message: String, density: Float, color: Int): TextView {
        val view = text(context, message, BODY_SP, color, Fonts.REGULAR)
        view.setLineSpacing(0f, LINE_SPACING)
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (12 * density).toInt() }
        return view
    }

    private fun text(
        context: Context,
        value: String,
        sizeSp: Float,
        color: Int,
        font: android.graphics.Typeface
    ): TextView {
        val view = TextView(context)
        view.text = value
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        view.setTextColor(color)
        view.typeface = font
        return view
    }

    private fun buttonRow(context: Context, density: Float): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (24 * density).toInt() }
        return row
    }

    private fun buttonView(context: Context, label: String, density: Float, index: Int): TextView {
        val view = TextView(context)
        view.text = label
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP)
        view.gravity = Gravity.CENTER
        view.tag = "m3:button"
        view.isFocusable = true
        view.isClickable = true
        val horizontal = (20 * density).toInt()
        val vertical = (10 * density).toInt()
        view.setPadding(horizontal, vertical, horizontal, vertical)
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { if (index > 0) marginStart = (10 * density).toInt() }
        return view
    }

    private const val WIDTH_DP = 450
    private const val PADDING_DP = 24
    private const val ELEVATION_DP = 12
    private const val TITLE_SP = 20f
    private const val BODY_SP = 14f
    private const val LINE_SPACING = 1.25f
    private const val DIM = 0.6f
}
