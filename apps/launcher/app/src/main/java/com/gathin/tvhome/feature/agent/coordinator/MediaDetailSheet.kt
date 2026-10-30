package com.gathin.tvhome.feature.agent.coordinator

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.motion.FocusScrollView
import com.gathin.tvhome.core.view.AgentCardData
import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.feature.agent.data.CatalogClient
import com.gathin.tvhome.feature.agent.data.TmdbMediaDetailResponse
import com.gathin.tvhome.feature.playback.PlaybackIdentity
import com.gathin.tvhome.feature.playback.PlaybackRouter
import com.gathin.tvhome.feature.playback.PlaybackStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.WeakHashMap

object MediaDetailSheet {
    private val open = WeakHashMap<Activity, Dialog>()

    fun close(activity: Activity) {
        MediaProviderSheet.close(activity)
        open.remove(activity)?.dismiss()
    }
    fun contains(activity: Activity, titleId: String): Boolean =
        open[activity]?.window?.decorView?.tag == titleId

    fun show(context: Context, card: AgentCardData) {
        val activity = PlaybackRouter.activity(context) ?: return
        close(activity)
        val previousFocus = if (activity.hasWindowFocus()) activity.window.decorView.findFocus()
            else null
        val request = AgentClient.Request()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val view = MediaDetailView(activity, card)
        val scroll = FocusScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(Palette.currentOrBaseline().surface)
            addView(view)
        }
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen)
        dialog.setContentView(scroll)
        dialog.window?.setWindowAnimations(0)
        val detail = card.actions.firstOrNull { it.name == "catalog_detail" }
        for (action in card.actions.filter { it !== detail }) {
            view.action(action.label) { AgentCardActionHandler.perform(activity, card, action) }
        }
        view.back.setOnClickListener { dialog.dismiss() }
        val lifecycle = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStopped(host: Activity) {
                if (host === activity) dialog.dismiss()
            }
            override fun onActivityDestroyed(host: Activity) {
                if (host === activity) dialog.dismiss()
            }
            override fun onActivityCreated(host: Activity, state: Bundle?) {}
            override fun onActivityStarted(host: Activity) {}
            override fun onActivityResumed(host: Activity) {}
            override fun onActivityPaused(host: Activity) {}
            override fun onActivitySaveInstanceState(host: Activity, state: Bundle) {}
        }
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
        dialog.setOnDismissListener {
            request.cancel()
            scope.cancel()
            activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
            if (open[activity] === dialog) {
                MediaProviderSheet.close(activity)
                open.remove(activity)
            }
            if (previousFocus?.isAttachedToWindow == true) previousFocus.requestFocus()
        }
        open[activity] = dialog
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        view.focusInitial()

        val isTmdb = card.sourceId.equals("tmdb", true) || card.id.contains("tmdb")
        if (isTmdb) {
            val status = view.status(activity.getString(R.string.media_loading_detail))
            scope.launch {
                val tmdbDetail = withContext(Dispatchers.IO) {
                    runCatching {
                        CatalogClient.getTmdbMediaDetail(activity, card.type, card.id, request)
                    }.getOrNull()
                }
                if (!dialog.isShowing) return@launch
                view.content.removeView(status)
                if (tmdbDetail == null) {
                    view.status(activity.getString(R.string.playback_error))
                    view.content.addView(MediaActionView(activity,
                        activity.getString(R.string.playback_retry)).apply {
                        setOnClickListener { show(activity, card) }
                    })
                    return@launch
                }
                renderTmdbDetail(activity, view, card, tmdbDetail)
            }
            return
        }

        val rawId = detail?.parameters?.get("title_id")?.toString().orEmpty()
            .ifBlank { card.id }
            .ifBlank { card.title }
        if (rawId.isBlank()) return
        val titleId = if (card.sourceId == "tmdb" && !rawId.contains(":") &&
            rawId != card.title) "tmdb:$rawId" else rawId

        dialog.window?.decorView?.tag = titleId
        val status = view.status(activity.getString(R.string.media_loading_detail))
        scope.launch {
            val title = withContext(Dispatchers.IO) {
                runCatching { CatalogClient.getTitle(activity, titleId, request) }.getOrNull()
                    ?: if (titleId != card.title && card.title.isNotBlank()) {
                        runCatching { CatalogClient.getTitle(activity, card.title, request) }
                            .getOrNull()
                    } else null
            }
            if (!dialog.isShowing) return@launch
            view.content.removeView(status)
            if (title == null) {
                view.status(activity.getString(R.string.playback_error))
                view.content.addView(MediaActionView(activity,
                    activity.getString(R.string.playback_retry)).apply {
                    setOnClickListener { show(activity, card) }
                })
                return@launch
            }
            val source = title.sourceId.ifBlank { card.sourceId }
            view.configureKind(title.kind)
            view.title.text = title.name
            val kind = title.kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
            view.metadata.text = listOf(title.year.takeIf { it > 0 }?.toString().orEmpty(),
                kind, source).filter { it.isNotBlank() }.joinToString(" · ")
            val first = title.episodes.firstOrNull { it.playable }
            fun resumeEpisode() = PlaybackStore(activity).items().firstNotNullOfOrNull { item ->
                if (item.parameters["source_id"] != source ||
                    item.parameters["title_id"] != title.id ||
                    item.progress !in 0.001f..0.949f) null
                else title.episodes.firstOrNull {
                    it.id == item.parameters["episode_id"] && it.playable
                }
            }
            if (first != null && source.isNotBlank()) {
                val play = view.action(activity.getString(R.string.media_play)) {
                    val episode = resumeEpisode() ?: first
                    PlaybackRouter.choose(activity, PlaybackIdentity(source, episode.id,
                        MediaDetailsPolicy.playbackTitle(title, episode),
                        card.posterUrl, title.id, title.kind, episode.url))
                }
                fun refresh() {
                    play.setText(if (resumeEpisode() != null) R.string.watch_resume
                        else R.string.media_play)
                }
                view.viewTreeObserver.addOnWindowFocusChangeListener { focused ->
                    if (focused) refresh()
                }
                refresh()
                view.actions.removeView(play)
                view.actions.addView(play, 0)
                view.focusPrimaryIfIdle(play)
            }
            if (MediaDetailsPolicy.showEntries(title)) {
                view.content.addView(CatalogEpisodesView(activity, title, source, card.posterUrl) {
                    PlaybackRouter.choose(activity, it)
                })
            }
        }
    }

    private fun renderTmdbDetail(
        activity: Activity,
        view: MediaDetailView,
        card: AgentCardData,
        detail: TmdbMediaDetailResponse
    ) {
        val title = detail.title
        view.configureKind(title.kind.ifBlank { card.type })
        if (title.name.isNotBlank()) view.title.text = title.name
        if (title.overview.isNotBlank()) {
            view.synopsis.text = title.overview
            view.synopsis.visibility = android.view.View.VISIBLE
        }
        val kind = (title.kind.ifBlank { card.type })
            .replace('_', ' ').replaceFirstChar { it.uppercase() }
        view.metadata.text = listOf(
            (if (title.year > 0) title.year else card.year).toString()
                .takeIf { it != "0" }.orEmpty(),
            kind, "tmdb"
        ).filter { it.isNotBlank() }.joinToString(" · ")

        val mediaCard = card.copy(title = title.name.ifBlank { card.title },
            type = title.kind.ifBlank { card.type },
            summary = title.overview.ifBlank { card.summary },
            posterUrl = title.poster.ifBlank { card.posterUrl },
            year = title.year.takeIf { it > 0 }?.toString() ?: card.year)
        for (group in detail.sources) {
            if (group.items.isEmpty()) continue
            if (group.playbackMode != -1) {
                TmdbSourcesView.add(activity, view, mediaCard, group)
                continue
            }
            MediaProviderSheet.add(activity, view.content, mediaCard, group.sourceId) { provider ->
                val density = activity.resources.displayMetrics.density
                val palette = Palette.currentOrBaseline()
                for (item in group.items) {
                    val cardContainer = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        clipChildren = false
                        clipToPadding = false
                        val padH = (24 * density).toInt()
                        val padV = (16 * density).toInt()
                        setPadding(padH, padV, padH, padV)
                        background = GradientDrawable().apply {
                            setColor(palette.surfaceContainerHigh)
                            cornerRadius = 16 * density
                            setStroke((1 * density).toInt(), palette.outlineVariant)
                        }
                        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = (12 * density).toInt()
                        }
                    }

                    val iconView = ImageView(activity).apply {
                        val isShow = item.kind == "show" || item.kind == "series" ||
                            card.type == "show" || card.type == "series"
                        setImageResource(if (isShow) R.drawable.ic_tv else R.drawable.ic_movie)
                        imageTintList = ColorStateList.valueOf(palette.primary)
                        val iconSize = (28 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                            marginEnd = (16 * density).toInt()
                        }
                    }
                    cardContainer.addView(iconView)

                    val infoBlock = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    val titleView = TextView(activity).apply {
                        text = item.title.ifBlank { title.name }
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setTextColor(palette.onSurface)
                        typeface = Fonts.MEDIUM
                    }
                    val isRequestProvider = group.sourceId.equals("radarr", true) ||
                        group.sourceId.equals("sonarr", true) ||
                        group.sourceId.equals("overseerr", true) ||
                        group.sourceId.equals("jellyseerr", true)
                    val descText = item.availability.ifBlank {
                        if (isRequestProvider) activity.getString(R.string.media_request_desc)
                        else item.summary
                    }
                    val subView = TextView(activity).apply {
                        text = descText.ifBlank { group.sourceId.uppercase() }
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(palette.onSurfaceVariant)
                        setPadding(0, (2 * density).toInt(), 0, 0)
                    }
                    infoBlock.addView(titleView)
                    infoBlock.addView(subView)
                    cardContainer.addView(infoBlock, LinearLayout.LayoutParams(0, -2, 1f).apply {
                        marginEnd = (16 * density).toInt()
                    })

                    val actionBox = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        clipChildren = false
                        clipToPadding = false
                    }
                    for ((index, action) in item.actions.withIndex()) {
                        val seasonNum = action.parameters["season"]?.toString()
                            ?: (action.parameters["seasons"] as? List<*>)?.firstOrNull()?.toString()
                            ?: action.parameters["season_number"]?.toString()
                        val seasonLabel = seasonNum != null &&
                            action.label.contains("mùa này", true)
                        val displayLabel = if (seasonLabel) {
                            action.label.replace("mùa này", "Mùa $seasonNum", true)
                        } else action.label

                        val btn = MediaActionView(activity, displayLabel, primary = (index == 0)).apply {
                            val params = LinearLayout.LayoutParams(-2, -2)
                            if (index < item.actions.size - 1) {
                                params.marginEnd = (10 * density).toInt()
                            }
                            layoutParams = params
                        }
                        btn.setOnClickListener {
                            if (action.name == "catalog_detail" || action.id == "catalog_detail") {
                                val itemId = action.parameters["item_id"]?.toString()
                                    ?: action.parameters["title_id"]?.toString().orEmpty()
                                CatalogEpisodeSheet.show(activity, itemId,
                                    group.sourceId, card.posterUrl)
                            } else {
                                AgentCardActionHandler.perform(activity, card, action)
                            }
                        }
                        actionBox.addView(btn)
                    }
                    cardContainer.addView(actionBox)
                    provider.content.addView(cardContainer)
                }
            }
        }
    }
}
