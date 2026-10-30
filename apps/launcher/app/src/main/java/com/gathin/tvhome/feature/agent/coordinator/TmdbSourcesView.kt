package com.gathin.tvhome.feature.agent.coordinator

import android.app.Activity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.gathin.tvhome.R
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.data.MediaSourceGroup
import com.gathin.tvhome.feature.agent.data.MediaSourceItem
import com.gathin.tvhome.feature.agent.data.TmdbMediaParser
import com.gathin.tvhome.feature.playback.PlaybackIdentity
import com.gathin.tvhome.feature.playback.PlaybackRouter

object TmdbSourcesView {
    fun add(activity: Activity, view: MediaDetailView, card: AgentCardData,
        source: MediaSourceGroup) {
        if (source.status != "ok") return
        MediaProviderSheet.add(activity, view.content, card, source.sourceId) { detail ->
            addContent(activity, detail.content, card, source)
        }
    }

    private fun addContent(activity: Activity, parent: LinearLayout, card: AgentCardData,
        source: MediaSourceGroup) {
        val hasSeasons = MediaDetailsPolicy.isSeries(card.type) &&
            source.items.any { it.season > 0 }
        if (hasSeasons) {
            val items = source.items.sortedBy { it.season.takeIf { it > 0 } ?: Int.MAX_VALUE }
            val labels = items.map { item ->
                if (item.season > 0) activity.getString(R.string.media_season_tab, item.season)
                else item.title
            }
            addTabs(activity, parent, labels) { index, body ->
                addItem(activity, body, card, source, items[index])
            }
            return
        }
        for (item in source.items) {
            addItem(activity, parent, card, source, item)
        }
    }

    private fun addItem(activity: Activity, body: LinearLayout, card: AgentCardData,
        source: MediaSourceGroup, item: MediaSourceItem) {
        if (source.playbackMode == 0 && item.url.isNotBlank()) {
            body.addView(MediaActionView(activity, item.title).apply {
                setOnClickListener {
                    val identity = PlaybackIdentity(source.sourceId, "", item.title)
                    PlaybackRouter.choose(activity, identity,
                        item.url, directAvailable = false)
                }
            })
        }
        if (source.playbackMode !in 1..2) return
        if (item.servers.size > 1) {
            val labels = item.servers.mapIndexed { index, server ->
                server.name.ifBlank { activity.getString(R.string.media_server_tab, index + 1) }
            }
            addTabs(activity, body, labels, compact = true) { index, episodes ->
                addEpisodes(activity, episodes, card, source,
                    item.copy(servers = listOf(item.servers[index])))
            }
        } else addEpisodes(activity, body, card, source, item)
    }

    private fun addEpisodes(activity: Activity, body: LinearLayout, card: AgentCardData,
        source: MediaSourceGroup, item: MediaSourceItem) {
        val title = TmdbMediaParser.title(source, item).copy(kind = item.kind.ifBlank { card.type })
        if (title.episodes.isEmpty()) return
        body.addView(CatalogEpisodesView(activity, title, source.sourceId,
            item.poster.ifBlank { card.posterUrl }) {
            PlaybackRouter.choose(activity, it,
                item.url.takeIf { source.playbackMode == 2 }.orEmpty())
        })
    }

    private fun addTabs(activity: Activity, parent: LinearLayout, labels: List<String>,
        compact: Boolean = false, select: (Int, LinearLayout) -> Unit) {
        val tabs = LinearLayout(activity)
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        })
        parent.addView(body)
        labels.forEachIndexed { index, label ->
            val button = if (compact) MediaServerChip(activity, label)
                else MediaActionView(activity, label)
            tabs.addView(button.apply {
                setOnClickListener {
                    for (position in 0 until tabs.childCount) {
                        tabs.getChildAt(position).isSelected = position == index
                    }
                    body.removeAllViews()
                    select(index, body)
                }
            })
        }
        tabs.getChildAt(0)?.performClick()
    }
}
