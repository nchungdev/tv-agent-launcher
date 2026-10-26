package com.gathin.tvhome.feature.main.coordinator

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Palette

class HomeThemeCoordinator(
    private val activity: Activity
) {
    private var currentPalette: Palette = Palette.currentOrBaseline()

    fun applyTheme(palette: Palette, root: View) {
        currentPalette = palette
        activity.window.setBackgroundDrawable(ColorDrawable(palette.surface))

        root.findViewById<TextView>(R.id.clock)?.setTextColor(palette.primary)
        root.findViewById<TextView>(R.id.ampm)?.setTextColor(palette.primary)
        root.findViewById<TextView>(R.id.date)?.setTextColor(palette.onSurfaceVariant)
        root.findViewById<TextView>(R.id.shamsiDate)?.setTextColor(palette.onSurfaceVariant)
        root.findViewById<TextView>(R.id.stats)?.setTextColor(palette.onSurfaceVariant)
        root.findViewById<TextView>(R.id.heroName)?.setTextColor(palette.onSurface)

        val divider = root.findViewById<View>(R.id.clockDivider)
        divider?.setBackgroundColor(palette.outlineVariant)

        val density = activity.resources.displayMetrics.density
        val editBar = root.findViewById<View>(R.id.editBar)
        editBar?.let { bar ->
            val drawable = GradientDrawable().apply {
                setColor(palette.secondaryContainer)
                cornerRadius = 28 * density
            }
            bar.background = drawable
            root.findViewById<TextView>(R.id.editBarTitle)
                ?.setTextColor(palette.onSecondaryContainer)
            root.findViewById<TextView>(R.id.editBarHint)
                ?.setTextColor(Palette.alpha(palette.onSecondaryContainer, 0xCC))
            root.findViewById<ImageView>(R.id.editBarIcon)
                ?.imageTintList = ColorStateList.valueOf(palette.onSecondaryContainer)
        }
    }

    fun getPalette(): Palette = currentPalette
}
