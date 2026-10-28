package com.gathin.tvhome.feature.main.widget

import android.view.View
import android.widget.LinearLayout
import com.gathin.tvhome.R

object HomeHubLayout {
    fun expanded(width: Int, availableHeight: Int, density: Float): Boolean =
        width / density >= 840 && availableHeight / density >= 250

    fun apply(shelf: View, expanded: Boolean) {
        val hub = shelf.findViewById<LinearLayout>(R.id.clockHub)
        val info = shelf.findViewById<View>(R.id.clockInfo)
        val tiles = shelf.findViewById<View>(R.id.smartHomeTiles)
        val direction = if (expanded) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        if (hub.orientation == direction) return
        hub.orientation = direction
        val infoParams = info.layoutParams as LinearLayout.LayoutParams
        infoParams.width = if (expanded) -1 else 0
        infoParams.weight = if (expanded) 0f else 1.2f
        info.layoutParams = infoParams
        val tileParams = tiles.layoutParams as LinearLayout.LayoutParams
        tileParams.width = if (expanded) -2 else 0
        tileParams.weight = if (expanded) 0f else 1f
        tileParams.marginStart = if (expanded) 0 else dp(shelf, 24)
        tileParams.topMargin = if (expanded) dp(shelf, 22) else 0
        tiles.layoutParams = tileParams
        for (id in intArrayOf(R.id.tileTemp, R.id.tileLight, R.id.tileSwitch)) {
            val tile = shelf.findViewById<View>(id)
            val params = tile.layoutParams as LinearLayout.LayoutParams
            params.width = if (expanded) dp(shelf, 88) else 0
            params.height = if (expanded) dp(shelf, 78) else -2
            params.weight = if (expanded) 0f else 1f
            tile.setPadding(0, dp(shelf, 8), 0, dp(shelf, 8))
            tile.layoutParams = params
        }
    }

    private fun dp(view: View, value: Int) = (value * view.resources.displayMetrics.density).toInt()
}
