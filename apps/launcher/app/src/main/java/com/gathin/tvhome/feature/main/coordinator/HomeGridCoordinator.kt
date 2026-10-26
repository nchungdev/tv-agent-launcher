package com.gathin.tvhome.feature.main.coordinator

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.main.adapter.AppAdapter
import com.gathin.tvhome.feature.main.adapter.Entry
import com.gathin.tvhome.feature.main.widget.HomeGrid

class HomeGridCoordinator(
    private val activity: Activity,
    private val columns: Int = 4,
    private val onTileFocused: (Entry?, Int) -> Unit,
    private val onTileClicked: (Entry, Int) -> Unit
) {
    private var grid: HomeGrid? = null
    private var adapter: AppAdapter? = null

    fun setup(root: View, appAdapter: AppAdapter) {
        adapter = appAdapter
        grid = root.findViewById(R.id.grid)
        val homeGrid = grid ?: return
        val headerRow = root.findViewById<View>(R.id.headerRow)
        val headerScrim = root.findViewById<View>(R.id.headerScrim)

        val density = activity.resources.displayMetrics.density
        val minHeaderHeight = Math.round(92 * density)

        if (headerRow != null) {
            homeGrid.setUnderlapSource { maxOf(headerRow.bottom, minHeaderHeight) }
            headerRow.viewTreeObserver?.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        val bottom = maxOf(headerRow.bottom, minHeaderHeight)
                        if (bottom > 0 && homeGrid.paddingTop != bottom) {
                            homeGrid.setPadding(
                                homeGrid.paddingLeft,
                                bottom,
                                homeGrid.paddingRight,
                                homeGrid.paddingBottom
                            )
                            homeGrid.headerChanged()
                        }
                    }
                }
            )
        } else {
            homeGrid.setUnderlapSource { minHeaderHeight }
        }

        if (headerScrim != null) {
            headerScrim.setBackgroundColor(Color.parseColor("#E6000000"))
            homeGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val underlap = homeGrid.underlap()
                    if (underlap > 0) {
                        val lp = headerScrim.layoutParams
                        if (lp.height != underlap) {
                            lp.height = underlap
                            headerScrim.layoutParams = lp
                        }
                        val offset = homeGrid.computeVerticalScrollOffset()
                        val progress = (offset.toFloat() / (underlap * 0.5f)).coerceIn(0f, 1f)
                        headerScrim.alpha = progress
                    }
                }
            })
        }

        homeGrid.layoutManager = object : GridLayoutManager(activity, columns) {
            override fun requestChildRectangleOnScreen(
                parent: RecyclerView,
                child: View,
                rect: Rect,
                immediate: Boolean,
                focusedChildVisible: Boolean
            ): Boolean {
                val top = child.top + rect.top
                val bottom = top + rect.height()
                val limitBottom = height - paddingBottom
                val dy = when {
                    top < homeGrid.underlap() -> top - homeGrid.underlap()
                    bottom <= height -> 0
                    else -> minOf(top, bottom - limitBottom)
                }
                if (dy == 0) return false
                parent.scrollBy(0, dy)
                return true
            }
        }

        val lm = homeGrid.layoutManager as GridLayoutManager
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (adapter?.isLibraryAt(position) == true) columns else 1
        }
        homeGrid.adapter = appAdapter
    }

    fun focusTile(position: Int) {
        val homeGrid = grid ?: return
        val count = adapter?.itemCount ?: 0
        if (position in 0 until count) {
            homeGrid.scrollToPosition(position)
            homeGrid.post {
                val vh = homeGrid.findViewHolderForAdapterPosition(position)
                vh?.itemView?.requestFocus()
            }
        }
    }

    fun focusDefault() {
        focusTile(0)
    }
}
