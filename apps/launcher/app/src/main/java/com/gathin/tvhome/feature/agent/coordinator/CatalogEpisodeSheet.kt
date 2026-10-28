package com.gathin.tvhome.feature.agent.coordinator

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.Shapes
import com.gathin.tvhome.feature.agent.data.CatalogClient
import com.gathin.tvhome.feature.agent.data.CatalogEpisode
import com.gathin.tvhome.feature.agent.data.CatalogPlayerLauncher
import com.gathin.tvhome.feature.agent.data.CatalogTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CatalogEpisodeSheet {

    private const val PAGE_SIZE = 25
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @JvmStatic
    fun show(context: Context, titleId: String) {
        val density = context.resources.displayMetrics.density
        val palette = Palette.currentOrBaseline()

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(palette.surfaceContainerHigh)
                cornerRadius = Shapes.LARGE * density
            }
        }

        val loading = ProgressBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        val loadingText = TextView(context).apply {
            text = "Đang tải danh sách tập…"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(palette.onSurfaceVariant)
            gravity = Gravity.CENTER
            setPadding(0, (12 * density).toInt(), 0, (16 * density).toInt())
        }

        root.addView(loading)
        root.addView(loadingText)

        dialog.setContentView(root)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(0))
            window.setLayout((480 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(0.6f)
        }
        dialog.show()

        scope.launch {
            val titleResult = withContext(Dispatchers.IO) {
                runCatching { CatalogClient.getTitle(context, titleId) }.getOrNull()
            }

            if (titleResult == null) {
                loading.visibility = View.GONE
                loadingText.text = "Không tải được danh sách tập hoặc máy chủ không hỗ trợ."
                val closeBtn = makeButton(context, "Đóng", density, palette) { dialog.dismiss() }
                root.addView(closeBtn)
                closeBtn.requestFocus()
                return@launch
            }

            renderTitle(context, dialog, root, titleResult, density, palette)
        }
    }

    private fun renderTitle(
        context: Context,
        dialog: Dialog,
        root: LinearLayout,
        title: CatalogTitle,
        density: Float,
        palette: Palette
    ) {
        root.removeAllViews()

        val headerText = buildString {
            append(title.name.ifBlank { "Chi tiết phim" })
            if (title.year > 0) append(" (${title.year})")
        }
        val titleView = TextView(context).apply {
            text = headerText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(palette.onSurface)
            typeface = Fonts.MEDIUM
        }
        root.addView(titleView)

        val allEpisodes = title.episodes
        if (allEpisodes.isEmpty()) {
            val emptyView = TextView(context).apply {
                text = "Chưa có tập phim nào sẵn sàng để phát."
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(palette.onSurfaceVariant)
                setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            }
            root.addView(emptyView)
            val close = makeButton(context, "Đóng", density, palette) { dialog.dismiss() }
            root.addView(close)
            close.requestFocus()
            return
        }

        val pageEpisodes = allEpisodes.take(PAGE_SIZE)
        val episodeScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (240 * density).toInt()
            ).apply {
                topMargin = (12 * density).toInt()
            }
        }

        val episodeList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (ep in pageEpisodes) {
            val label = ep.label.ifBlank { "Tập ${ep.order}" }
            val epBtn = makeEpisodeRow(context, label, density, palette) {
                dialog.dismiss()
                playEpisode(context, ep, "${title.name} - $label")
            }
            episodeList.addView(epBtn)
        }

        episodeScroll.addView(episodeList)
        root.addView(episodeScroll)

        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (10 * density).toInt(), 0, 0)
        }
        val closeBtn = makeButton(context, "Đóng", density, palette) { dialog.dismiss() }
        bottomRow.addView(closeBtn)
        root.addView(bottomRow)

        episodeList.getChildAt(0)?.requestFocus()
    }

    private fun playEpisode(context: Context, episode: CatalogEpisode, fullTitle: String) {
        Toast.makeText(context, "Đang mở $fullTitle…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val playback = withContext(Dispatchers.IO) {
                runCatching { CatalogClient.resolvePlayback(context, episode.id) }.getOrNull()
            }
            if (playback == null || playback.playback.url.isBlank()) {
                Toast.makeText(context, "Không thể lấy luồng phát cho tập này.", Toast.LENGTH_LONG).show()
                return@launch
            }
            CatalogPlayerLauncher.play(context, playback.playback, fullTitle)
        }
    }

    private fun makeEpisodeRow(
        context: Context,
        label: String,
        density: Float,
        palette: Palette,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.onSurface)
            val padH = (12 * density).toInt()
            val padV = (8 * density).toInt()
            setPadding(padH, padV, padH, padV)
            isFocusable = true
            isClickable = true
            background = GradientDrawable().apply {
                setColor(0x14FFFFFF)
                cornerRadius = 8 * density
            }
            setOnFocusChangeListener { _, hasFocus ->
                (background as? GradientDrawable)?.apply {
                    if (hasFocus) {
                        setColor(Palette.alpha(palette.primary, 0x38))
                        setStroke((2 * density).toInt(), palette.primary)
                    } else {
                        setColor(0x14FFFFFF)
                        setStroke(0, 0)
                    }
                }
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (6 * density).toInt()
            }
        }
    }

    private fun makeButton(
        context: Context,
        label: String,
        density: Float,
        palette: Palette,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.onSurface)
            typeface = Fonts.MEDIUM
            val padH = (16 * density).toInt()
            val padV = (8 * density).toInt()
            setPadding(padH, padV, padH, padV)
            isFocusable = true
            isClickable = true
            background = GradientDrawable().apply {
                setColor(Palette.alpha(palette.primary, 0x22))
                cornerRadius = 8 * density
            }
            setOnFocusChangeListener { _, hasFocus ->
                (background as? GradientDrawable)?.apply {
                    if (hasFocus) {
                        setColor(Palette.alpha(palette.primary, 0x55))
                        setStroke((2 * density).toInt(), palette.primary)
                    } else {
                        setColor(Palette.alpha(palette.primary, 0x22))
                        setStroke(0, 0)
                    }
                }
            }
            setOnClickListener { onClick() }
        }
    }
}
