package com.gathin.tvhome.feature.playback

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.core.design.Shapes

object PlaybackChoices {

    fun choose(
        activity: Activity,
        source: String,
        providerUrl: String = "",
        directAvailable: Boolean = true,
        rememberSelection: Boolean = true,
        selected: (String) -> Unit
    ) {
        val store = PlaybackStore(activity)
        store.remember(source)
        val density = activity.resources.displayMetrics.density

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(0xF018181C.toInt())
                cornerRadius = Shapes.EXTRA_LARGE * density
                setStroke((1 * density).toInt(), 0x33FFFFFF)
            }
        }

        val title = TextView(activity).apply {
            text = source
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.WHITE)
            typeface = Fonts.MEDIUM
        }
        val subtitle = TextView(activity).apply {
            setText(R.string.playback_settings)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xAAFFFFFF.toInt())
            typeface = Fonts.REGULAR
        }

        panel.addView(title)
        panel.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (4 * density).toInt()
            bottomMargin = (16 * density).toInt()
        })

        val rememberBox = CheckBox(activity).apply {
            setText(R.string.playback_remember)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding((8 * density).toInt(), 0, 0, 0)
        }

        val launcherBtn = optionCard(activity, density,
            activity.getString(R.string.playback_launcher),
            activity.getString(R.string.playback_launcher_description)) {
            if (rememberBox.isChecked) store.prefer(source, "launcher")
            dialog.dismiss()
            selected("launcher")
        }

        val appBtn = optionCard(activity, density,
            activity.getString(R.string.playback_external_player),
            activity.getString(R.string.playback_external_description)) {
            if (rememberBox.isChecked) store.prefer(source, "app")
            dialog.dismiss()
            selected("app")
        }

        if (directAvailable) {
            panel.addView(launcherBtn)
            panel.addView(appBtn, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (10 * density).toInt()
            })
        }
        val providerBtn = if (providerUrl.isNotBlank()) {
            optionCard(activity, density, activity.getString(R.string.media_open_provider),
                activity.getString(R.string.playback_provider_description)) {
                dialog.dismiss()
                selected("provider")
            }.also {
                panel.addView(it, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = (10 * density).toInt()
                })
            }
        } else null

        rememberBox.visibility = if (rememberSelection && directAvailable)
            android.view.View.VISIBLE else android.view.View.GONE
        panel.addView(rememberBox, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (16 * density).toInt()
            bottomMargin = (12 * density).toInt()
        })

        val cancelBtn = TextView(activity).apply {
            setText(android.R.string.cancel)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xAAFFFFFF.toInt())
            typeface = Fonts.MEDIUM
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            val padH = (16 * density).toInt()
            val padV = (10 * density).toInt()
            setPadding(padH, padV, padH, padV)
            setOnFocusChangeListener { _, focused ->
                setTextColor(if (focused) Color.WHITE else 0xAAFFFFFF.toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(if (focused) 0x33FFFFFF else Color.TRANSPARENT)
                }
            }
            setOnClickListener { dialog.dismiss() }
        }

        val footer = LinearLayout(activity).apply {
            gravity = Gravity.END
            addView(cancelBtn)
        }
        panel.addView(footer)

        dialog.setContentView(panel)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout((420 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(0.6f)
        }
        PlaybackPrompts.showDialog(activity, dialog)
        (if (directAvailable) launcherBtn else providerBtn)?.requestFocus()
    }

    private fun optionCard(
        context: Context,
        density: Float,
        title: String,
        subtext: String,
        action: () -> Unit
    ): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            val padH = (16 * density).toInt()
            val padV = (12 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(0x22FFFFFF)
                cornerRadius = 12 * density
            }
            setOnClickListener { action() }
            setOnFocusChangeListener { view, focused ->
                background = GradientDrawable().apply {
                    setColor(if (focused) 0xFF0284C7.toInt() else 0x22FFFFFF)
                    cornerRadius = 12 * density
                }
                view.animate().scaleX(if (focused) 1.03f else 1f)
                    .scaleY(if (focused) 1.03f else 1f).setDuration(150).start()
            }
        }

        val mainText = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
            typeface = Fonts.MEDIUM
        }

        val detailText = TextView(context).apply {
            text = subtext
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xCCFFFFFF.toInt())
            typeface = Fonts.REGULAR
        }

        card.addView(mainText)
        card.addView(detailText, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (2 * density).toInt()
        })
        return card
    }

    fun settings(activity: Activity) {
        val store = PlaybackStore(activity)
        val sources = store.sources().sorted()
        val labels = sources.map { source ->
            val choice = when (store.preferred(source)) {
                "launcher" -> activity.getString(R.string.playback_launcher)
                "app" -> activity.getString(R.string.playback_app)
                else -> activity.getString(R.string.playback_ask)
            }
            "$source · $choice"
        }
        PlaybackPrompts.show(activity, AlertDialog.Builder(activity)
            .setTitle(R.string.playback_settings)
            .setItems(labels.toTypedArray()) { _, index ->
                val source = sources[index]
                val players = arrayOf(activity.getString(R.string.playback_ask),
                    activity.getString(R.string.playback_launcher),
                    activity.getString(R.string.playback_app))
                PlaybackPrompts.show(activity, AlertDialog.Builder(activity)
                    .setTitle(source)
                    .setItems(players) { _, choice ->
                        store.prefer(source, listOf("", "launcher", "app")[choice])
                    })
            }.setMessage(if (sources.isEmpty()) activity.getString(R.string.playback_no_sources)
                else null).setNegativeButton(android.R.string.cancel, null))
    }
}
