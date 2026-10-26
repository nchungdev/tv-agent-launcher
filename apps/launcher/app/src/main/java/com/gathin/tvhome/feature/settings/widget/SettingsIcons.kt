package com.gathin.tvhome.feature.settings.widget
import com.gathin.tvhome.feature.settings.data.*
import com.gathin.tvhome.feature.settings.ui.*

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import com.gathin.tvhome.core.design.Palette


object SettingsIcons {

    fun icon(activity: Activity, palette: Palette, res: Int): Drawable {
        val d = activity.getDrawable(res)!!.mutate()
        d.setTintList(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(palette.inverseOnSurface, palette.onSurfaceVariant)
            )
        )
        return d
    }
}
