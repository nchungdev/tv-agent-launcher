package com.gathin.tvhome.feature.player

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.Shapes
import com.gathin.tvhome.feature.agent.data.CatalogEpisode

object PickerDialog {

    fun show(context: Context, request: PlayRequest, onPick: (CatalogEpisode) -> Unit): Dialog {
        val density = context.resources.displayMetrics.density
        val palette = Palette.dark(Palette.currentOrBaseline())
        val dialog = Dialog(context)
        val padding = (24 * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                setColor(palette.surfaceContainerHigh)
                cornerRadius = Shapes.EXTRA_LARGE * density
            }
        }
        root.addView(TextView(context).apply {
            text = request.titleName
            typeface = Fonts.MEDIUM
            setTextColor(palette.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, 0, 0, (14 * density).toInt())
        })
        val picker = EpisodePicker(context, request) { episode ->
            dialog.dismiss()
            onPick(episode)
        }
        root.addView(picker)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(0))
            window.setLayout((WIDTH_DP * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(DIM)
        }
        dialog.show()
        picker.post { picker.focusCurrent() }
        return dialog
    }

    private const val WIDTH_DP = 640
    private const val DIM = 0.6f
}
