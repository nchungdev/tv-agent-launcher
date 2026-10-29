package com.gathin.tvhome.feature.agent.coordinator

import android.app.Activity
import android.content.Context
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.core.view.CardAction

object CatalogEpisodeSheet {
    fun close(activity: Activity) = MediaDetailSheet.close(activity)

    fun show(context: Context, titleId: String, sourceId: String = "", poster: String = "") {
        val action = CardAction("detail", "", CardAction.KIND_CLIENT, "catalog_detail",
            mapOf("title_id" to titleId, "source_id" to sourceId))
        MediaDetailSheet.show(context, AgentCardData("information", titleId, "", "", "",
            poster, titleId, listOf(action), sourceId, false))
    }
}
