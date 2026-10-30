package com.gathin.tvhome.feature.agent.coordinator

import android.app.Activity
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.motion.FocusScrollView
import com.gathin.tvhome.feature.agent.data.CatalogTitle
import com.gathin.tvhome.feature.playback.PlaybackIdentity

class CatalogEpisodesView(
    activity: Activity, private val title: CatalogTitle, private val source: String,
    private val poster: String, private val play: (PlaybackIdentity) -> Unit
) : LinearLayout(activity) {
    private val density = resources.displayMetrics.density
    private var columns = 1
    private var firstEntry: android.view.View? = null
    private var page = 0
    private var season = ""
    private val list = LinearLayout(activity).apply { orientation = VERTICAL }
    private val scroll = FocusScrollView(activity).apply { addView(list) }
    private val previous = MediaActionView(activity, "‹")
    private val count = TextView(activity)
    private val next = MediaActionView(activity, "›")

    init {
        orientation = VERTICAL
        val palette = com.gathin.tvhome.core.design.Palette.currentOrBaseline()
        setPadding(0, (18 * density).toInt(), 0, 0)
        val series = MediaDetailsPolicy.isSeries(title.kind)
        val heading = TextView(activity).apply {
            setText(if (series) R.string.playback_episodes else R.string.media_versions)
            textSize = 16f
            typeface = com.gathin.tvhome.core.design.Fonts.MEDIUM
            setTextColor(palette.onSurface)
            setPadding(0, 0, 0, (10 * density).toInt())
        }
        addView(heading)
        if (series && title.seasons.isNotEmpty()) {
            val seasons = LinearLayout(activity)
            val strip = android.widget.HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                addView(seasons)
            }
            fun seasonButton(label: String, id: String) {
                seasons.addView(MediaActionView(activity, label).apply {
                    setOnClickListener {
                        season = id
                        page = 0
                        paint()
                        for (index in 0 until seasons.childCount) {
                            val button = seasons.getChildAt(index)
                            button.isSelected = button === this
                        }
                    }
                })
            }
            seasonButton(activity.getString(R.string.media_all_episodes), "")
            title.seasons.forEach { seasonButton(it.label, it.id) }
            seasons.getChildAt(0).isSelected = true
            addView(strip, LayoutParams(-1, -2))
        }
        scroll.isVerticalScrollBarEnabled = false
        addView(scroll, LayoutParams(-1, minOf((140 * density).toInt(),
            resources.displayMetrics.heightPixels / 3)).apply {
            topMargin = (20 * density).toInt()
        })
        val navigation = LinearLayout(activity).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        count.setTextColor(palette.onSurfaceVariant)
        count.textSize = 12f
        count.setPadding(0, 0, (12 * density).toInt(), 0)
        navigation.addView(previous)
        navigation.addView(count)
        navigation.addView(next)
        addView(navigation)
        previous.setOnClickListener { if (page > 0) { page--; paint(); firstFocus() } }
        next.setOnClickListener {
            if ((page + 1) * 25 < episodes().size) { page++; paint(); firstFocus() }
        }
        paint()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val nextColumns = if (MediaDetailsPolicy.isSeries(title.kind) &&
            width / density >= 720) 3 else 1
        if (nextColumns != columns) {
            val focused = list.findFocus()?.tag
            columns = nextColumns
            paint()
            if (focused != null) list.findViewWithTag<android.view.View>(focused)?.requestFocus()
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun episodes() = title.episodes.filter { season.isEmpty() || it.seasonId == season }

    private fun firstFocus() { firstEntry?.requestFocus() }

    private fun paint() {
        list.removeAllViews()
        val episodes = episodes()
        val start = page * 25
        firstEntry = null
        val series = MediaDetailsPolicy.isSeries(title.kind)
        for (entries in episodes.drop(start).take(25).chunked(columns)) {
            val row = LinearLayout(context)
            for (episode in entries) {
                val card = MediaEpisodeRow(context, episode, series).apply {
                    tag = episode.id
                    isEnabled = episode.playable && source.isNotBlank()
                    setOnClickListener { play(PlaybackIdentity(source, episode.id,
                        MediaDetailsPolicy.playbackTitle(title, episode),
                        poster, episode.titleId.ifBlank { title.id }, title.kind, episode.url)) }
                }
                if (firstEntry == null) firstEntry = card
                val cardHeight = ((if (series) 64 else 48) * density).toInt()
                row.addView(card, LayoutParams(0, cardHeight, 1f).apply {
                    if (row.childCount > 0) marginStart = (16 * density).toInt()
                })
            }
            repeat(columns - entries.size) {
                row.addView(android.view.View(context), LayoutParams(0, 1, 1f).apply {
                    marginStart = (16 * density).toInt()
                })
            }
            list.addView(row, LayoutParams(-1, -2).apply {
                bottomMargin = ((if (series) 12 else 6) * density).toInt()
            })
        }
        count.text = "${page + 1} / ${((episodes.size + 24) / 25).coerceAtLeast(1)}"
        previous.isEnabled = page > 0
        next.isEnabled = start + 25 < episodes.size
        scroll.scrollTo(0, 0)
        if (episodes.isEmpty()) count.setText(R.string.watch_empty)
    }
}
