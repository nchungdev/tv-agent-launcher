package com.gathin.tvhome.feature.notification.ui
import com.gathin.tvhome.feature.notification.data.*
import com.gathin.tvhome.feature.notification.presenter.*
import com.gathin.tvhome.feature.notification.widget.*

import com.gathin.tvhome.feature.notification.presenter.NotificationPresenter
import com.gathin.tvhome.feature.notification.presenter.NotificationIntent
import com.gathin.tvhome.feature.notification.presenter.NotificationState
import com.gathin.tvhome.feature.notification.presenter.NotificationEffect
import com.gathin.tvhome.feature.notification.data.TvNotification
import com.gathin.tvhome.feature.notification.data.NotificationRepositoryImpl
import com.gathin.tvhome.core.view.SideSheet
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.feature.notification.widget.NotificationHudOverlay
import com.gathin.tvhome.core.util.ImageLoader
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.motion.FocusScrollView
import com.gathin.tvhome.feature.agent.coordinator.AgentActionExecutor
import com.gathin.tvhome.core.util.dialogScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob



class NotificationSheet(
    private val activity: Activity,
    private val config: Config,
    private val onDismissed: (() -> Unit)? = null
) {
    private val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val presenter by lazy { NotificationPresenter(scope) }

    private val scrim: View
    private val panel: LinearLayout
    private val rows: LinearLayout
    private val footer: LinearLayout
    private val summary: TextView
    private val sideSheet: SideSheet
    private var liveListener: ((Int) -> Unit)? = null

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LayoutInflater.from(activity).inflate(R.layout.activity_notifications, null)
        dialog.setContentView(root)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        scrim = root.findViewById(R.id.scrim)
        panel = root.findViewById(R.id.panel)
        rows = root.findViewById(R.id.rows)
        footer = root.findViewById(R.id.footer)
        summary = root.findViewById(R.id.summary)
        sideSheet = SideSheet(activity, scrim, panel, config) {
            dialog.dismiss()
            onDismissed?.invoke()
        }
        val rowsScroll = root.findViewById<FocusScrollView>(R.id.rowsScroll)
        rowsScroll.setMotion(config.animations, config.reducedMotion)
        scrim.setOnClickListener { sideSheet.close() }
        dialog.setOnCancelListener { sideSheet.close() }
        dialog.setOnDismissListener {
            liveListener?.let { NotificationRepositoryImpl.getInstance().removeListener(it) }
            liveListener = null
            scope.cancel()
        }
        reskinPanel()
    }

    fun isShowing(): Boolean = dialog.isShowing

    fun show() {
        NotificationHudOverlay.dismiss()
        val appContext = activity.applicationContext
        scope.launch(Dispatchers.IO) { NotificationRepositoryImpl.getInstance().init(appContext) }

        scope.launch {
            presenter.state.collect { state -> render(state) }
        }
        scope.launch {
            presenter.effects.collect { effect ->
                when (effect) {
                    is NotificationEffect.ExecuteAction -> AgentActionExecutor.execute(
                        activity, effect.item
                    )
                    is NotificationEffect.Close -> close()
                }
            }
        }

        val listener: (Int) -> Unit = { count ->
            if (count > 0) presenter.processIntent(NotificationIntent.Load)
        }
        liveListener = listener
        NotificationRepositoryImpl.getInstance().addListener(listener)

        dialog.show()
        sideSheet.slideIn()
        presenter.processIntent(NotificationIntent.Load)
    }

    fun close() {
        if (sideSheet.closing()) return
        sideSheet.close()
    }

    private fun render(state: NotificationState) {
        rows.removeAllViews()
        footer.removeAllViews()
        val list = state.items
        if (list.isEmpty()) {
            summary.setText(R.string.notifications_empty)
            return
        }
        summary.text = "${list.size} thông báo gần đây"
        val density = activity.resources.displayMetrics.density
        for (item in list) {
            addNotificationRow(item, density)
        }
        addFooterButton()
        reskinPanel()
        rows.getChildAt(0)?.requestFocus()
    }

    private fun addNotificationRow(item: TvNotification, density: Float) {
        val row = LayoutInflater.from(activity).inflate(R.layout.item_notification, rows, false)
        val p = Palette.currentOrBaseline()
        val presentation = NotificationPresenter.present(item, p)

        val iconView = row.findViewById<ImageView>(R.id.icon)
        iconView.setImageResource(presentation.iconRes)
        val badgeColor = presentation.badgeColor
        iconView.setColorFilter(badgeColor)

        val sourceView = row.findViewById<TextView>(R.id.source)
        val dotView = row.findViewById<View>(R.id.dot)
        val timeView = row.findViewById<TextView>(R.id.time)
        val titleView = row.findViewById<TextView>(R.id.title)
        val messageView = row.findViewById<TextView>(R.id.message)
        val actionHint = row.findViewById<TextView>(R.id.actionHint)
        val thumbView = row.findViewById<ImageView>(R.id.mediaThumb)

        sourceView.text = presentation.badgeText
        sourceView.setTextColor(badgeColor)
        timeView.text = formatRelativeTime(item.timestampEpochMs)
        timeView.setTextColor(p.onSurfaceVariant)
        titleView.text = presentation.headline
        titleView.setTextColor(p.onSurface)
        messageView.text = presentation.body
        messageView.setTextColor(p.onSurfaceVariant)
        messageView.visibility = if (presentation.body.isEmpty()) View.GONE else View.VISIBLE
        dotView.visibility = if (!item.isRead) View.VISIBLE else View.GONE
        dotView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(badgeColor)
        }

        if (presentation.hasMediaThumb) {
            thumbView.visibility = View.VISIBLE
            val reqW = Math.round(92f * density)
            val reqH = Math.round(62f * density)
            ImageLoader.load(item.imageUrl, thumbView, reqW, reqH)
        } else {
            thumbView.visibility = View.GONE
        }

        val actionable = item.hasAction() || presentation.hasMediaThumb
        if (actionable && presentation.actionLabel.isNotEmpty()) {
            actionHint.text = presentation.actionLabel
            actionHint.setTextColor(p.onSecondaryContainer)
            actionHint.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(p.secondaryContainer)
                val strokeW = Math.max(1, Math.round(1f * density))
                setStroke(strokeW, Palette.alpha(p.primary, 0x66))
            }
            actionHint.visibility = View.VISIBLE
        }

        styleCardFocus(row, badgeColor, density)
        row.setOnClickListener {
            presenter.processIntent(NotificationIntent.ClickItem(item))
        }
        rows.addView(row)
    }

    private fun styleCardFocus(card: View, accentColor: Int, density: Float) {
        val p = Palette.currentOrBaseline()
        val radius = 16f * density
        val applyState = { focused: Boolean ->
            card.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                val fillColor = if (focused) {
                    p.surfaceContainerHighest
                } else {
                    p.surfaceContainer
                }
                setColor(fillColor)
                val strokeW = Math.max(1, Math.round((if (focused) 2f else 1f) * density))
                val strokeColor = if (focused) {
                    accentColor
                } else {
                    Palette.alpha(p.outlineVariant, 0x80)
                }
                setStroke(strokeW, strokeColor)
            }
            if (config.animations && !config.reducedMotion) {
                val scale = if (focused) 1.015f else 1f
                card.animate().scaleX(scale).scaleY(scale).setDuration(140L).start()
            }
        }
        applyState(false)
        card.setOnFocusChangeListener { _, hasFocus -> applyState(hasFocus) }
    }

    private fun addFooterButton() {
        val row = LayoutInflater.from(activity).inflate(R.layout.item_input, footer, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_clear_all)
        row.findViewById<TextView>(R.id.title).setText(R.string.clear_all)
        row.findViewById<View>(R.id.dot).visibility = View.GONE
        row.findViewById<TextView>(R.id.status).visibility = View.GONE
        row.setOnClickListener {
            presenter.processIntent(NotificationIntent.ClearAll)
        }
        footer.addView(row)
    }

    private fun formatRelativeTime(timestampMs: Long): String {
        val diff = (System.currentTimeMillis() - timestampMs) / 1000
        return when {
            diff < 60 -> "Vừa xong"
            diff < 3600 -> "${diff / 60}p trước"
            diff < 86400 -> "${diff / 3600}h trước"
            else -> "${diff / 86400} ngày trước"
        }
    }

    private fun reskinPanel() {
        val p = Palette.currentOrBaseline()
        val density = activity.resources.displayMetrics.density
        panel.background = GradientDrawable().apply {
            setColor(p.surfaceContainerHigh)
            cornerRadius = M3Skin.DIALOG_RADIUS_DP * density
        }
        val pad = Math.round(14f * density)
        panel.setPadding(pad, pad, pad, Math.round(16f * density))
        panel.elevation = 6f * density
        panel.findViewById<TextView>(R.id.title)?.setTextColor(p.onSurface)
        summary.setTextColor(p.onSurfaceVariant)
        M3Skin.apply(footer, p, config.animations, config.reducedMotion)
    }
}
