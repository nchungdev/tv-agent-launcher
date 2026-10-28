package com.gathin.tvhome.core.view

data class CardAction(
    val id: String,
    val label: String,
    val kind: String,
    val name: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val confirm: String = ""
) {
    val isServer: Boolean get() = kind == KIND_SERVER

    companion object {
        const val KIND_SERVER = "server"
        const val KIND_CLIENT = "client"
    }
}

data class AgentCardData(
    val status: String,
    val title: String,
    val type: String,
    val year: String,
    val link: String,
    val posterUrl: String = "",
    val id: String = "",
    val actions: List<CardAction> = emptyList(),
    val source: String = "",
    val season: Int = 0,
    val episode: Int = 0
) {
    fun resolvedActions(): List<CardAction> {
        if (actions.isNotEmpty()) return actions
        return when {
            isPlex() -> listOf(openInPlex())
            status.equals(STATUS_DOWNLOADING, ignoreCase = true) -> emptyList()
            status.equals(STATUS_REQUESTED, ignoreCase = true) -> emptyList()
            else -> listOf(requestMedia())
        }
    }

    fun isWeb(): Boolean = status.equals(STATUS_WEB, ignoreCase = true)

    fun isPlex(): Boolean {
        return status.equals(STATUS_PLEX, ignoreCase = true) ||
            status.equals(STATUS_AVAILABLE, ignoreCase = true)
    }

    private fun openInPlex(): CardAction {
        val parameters = if (link.isBlank()) emptyMap() else mapOf<String, Any?>("uri" to link)
        return CardAction(ID_OPEN, LABEL_OPEN, CardAction.KIND_CLIENT, "plex.open", parameters)
    }

    private fun requestMedia(): CardAction {
        val parameters = mapOf<String, Any?>(
            "title" to title,
            "year" to year,
            "media_type" to if (type.equals("Series", ignoreCase = true)) "series" else "movie"
        )
        val noun = if (type.equals("Series", ignoreCase = true)) "series" else "phim lẻ"
        val confirm = "Bạn có muốn thêm $noun \"$title\" vào thư viện để NAS tự tìm kiếm không?"
        return CardAction(
            ID_REQUEST, LABEL_REQUEST, CardAction.KIND_SERVER, "media.request", parameters, confirm
        )
    }

    companion object {
        const val STATUS_PLEX = "plex"
        const val STATUS_AVAILABLE = "available"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_REQUESTED = "requested"
        const val STATUS_WEB = "web"

        fun siteOf(url: String): String = runCatching {
            java.net.URI(url).host.orEmpty().removePrefix("www.")
        }.getOrDefault("")
        private const val ID_OPEN = "open"
        private const val ID_REQUEST = "request"
        private const val LABEL_OPEN = "Mở trong Plex"
        private const val LABEL_REQUEST = "Thêm vào NAS"
    }
}
