package com.gathin.tvhome.feature.agent.coordinator

import com.gathin.tvhome.feature.agent.data.CatalogEpisode
import com.gathin.tvhome.feature.agent.data.CatalogTitle

object MediaDetailsPolicy {
    fun isSeries(kind: String): Boolean = kind.lowercase() in setOf("series", "show", "season")
    fun showEntries(title: CatalogTitle): Boolean = isSeries(title.kind) || title.episodes.size > 1
    fun playbackTitle(title: CatalogTitle, episode: CatalogEpisode): String =
        if (isSeries(title.kind) && episode.label.isNotBlank()) title.name + " · " + episode.label
        else title.name
}
