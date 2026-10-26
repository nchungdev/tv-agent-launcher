package com.gathin.tvhome.core.design

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.main.adapter.AppAdapter
import com.gathin.tvhome.feature.main.adapter.AppTileViewHolder

class DockDecoration(
    private var dockSize: Int,
    private val density: Float
) : RecyclerView.ItemDecoration() {

    private var rtl = false
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1f, density / 2f)
    }
    private val rect = RectF()

    init {
        setPalette(Palette.currentOrBaseline())
    }

    fun setDockSize(size: Int) {
        dockSize = size
    }

    fun setPalette(p: Palette) {
        band.color = Palette.alpha(p.surfaceContainerHigh, BAND_ALPHA)
        edge.color = Palette.alpha(p.outlineVariant, EDGE_ALPHA)
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos in 0 until dockSize) {
            outRect.top = 0
            outRect.bottom = (12 * density).toInt()
            val shift = centringShift(parent, state)
            outRect.left = shift
            outRect.right = -shift
        }
    }

    private fun centringShift(parent: RecyclerView, state: RecyclerView.State): Int {
        val adapter = parent.adapter ?: return 0
        var tiles = 0
        val count = minOf(dockSize, state.itemCount)
        for (i in 0 until count) {
            if (adapter.getItemViewType(i) == AppAdapter.TILE_VIEW) tiles++
        }
        if (tiles == 0 || tiles >= dockSize) return 0
        val column = (parent.width - parent.paddingLeft - parent.paddingRight) / dockSize.toFloat()
        val shift = Math.round((dockSize - tiles) * column / 2f)
        return if (rtl) -shift else shift
    }

    fun setRtl(rtl: Boolean) {
        this.rtl = rtl
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        var any = false
        var cardWidth = 0
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos < 0 || pos >= dockSize) continue
            val vh = parent.getChildViewHolder(child)
            val card = if (vh is AppTileViewHolder) vh.card else child.findViewById<View>(R.id.card)
            if (card == null) continue
            val l = child.left + card.left.toFloat()
            val t = child.top + card.top.toFloat() + child.translationY
            val r = l + card.width
            val b = t + card.height
            cardWidth = card.width
            if (!any) {
                rect.set(l, t, r, b)
                any = true
            } else {
                rect.union(l, t, r, b)
            }
        }
        if (!any) return
        val pad = PADDING_DP * density
        rect.inset(-pad, -pad)
        val radius = AppAdapter.cornerRadius(cardWidth, density, Shapes.TILE_REST) + pad
        c.drawRoundRect(rect, radius, radius, band)
        val half = edge.strokeWidth / 2f
        rect.inset(half, half)
        c.drawRoundRect(rect, radius - half, radius - half, edge)
    }

    companion object {
        const val PADDING_DP = 12f
        private const val BAND_ALPHA = 0xB3
        private const val EDGE_ALPHA = 0x80
    }
}
