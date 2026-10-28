package com.gathin.tvhome.feature.account.ui

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gathin.tvhome.R
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.settings.data.Config

class GoogleAccountSheet(
    private val activity: Activity,
    private val config: Config,
    private val onChoose: () -> Unit,
    private val onManage: () -> Unit
) {
    private var dialog: Dialog? = null
    private var accountLabel: TextView? = null

    fun show(accountName: String?) {
        if (dialog?.isShowing == true) return

        val content = LayoutInflater.from(activity).inflate(
            R.layout.activity_quick_settings,
            activity.findViewById<ViewGroup>(android.R.id.content), false
        )
        val panel = content.findViewById<LinearLayout>(R.id.panel)
        val density = activity.resources.displayMetrics.density
        panel.layoutParams.width = minOf(
            (360 * density).toInt(), activity.resources.displayMetrics.widthPixels / 2
        )
        val rows = content.findViewById<LinearLayout>(R.id.rows)
        val label = TextView(activity).apply {
            setPadding((16 * density).toInt(), (12 * density).toInt(),
                (16 * density).toInt(), (12 * density).toInt())
            setTextColor(Palette.currentOrBaseline().onSurface)
            textSize = 15f
        }
        accountLabel = label
        update(accountName)
        rows.addView(label)
        addAction(rows, R.string.google_account_choose, onChoose)
        addAction(rows, R.string.google_account_manage, onManage)
        M3Skin.apply(panel, Palette.currentOrBaseline(), config.animations, config.reducedMotion)

        val sheet = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        sheet.setContentView(content)
        content.setOnClickListener { close() }
        sheet.setOnDismissListener {
            accountLabel = null
            dialog = null
        }
        dialog = sheet
        sheet.show()
        rows.getChildAt(1).requestFocus()
    }

    fun update(accountName: String?) {
        accountLabel?.text = accountName ?: activity.getString(R.string.google_account_not_shared)
    }

    private fun addAction(rows: LinearLayout, title: Int, action: () -> Unit) {
        val row = LayoutInflater.from(activity).inflate(R.layout.item_input, rows, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_person)
        row.findViewById<TextView>(R.id.title).setText(title)
        row.findViewById<View>(R.id.dot).visibility = View.GONE
        row.findViewById<View>(R.id.status).visibility = View.GONE
        row.setOnClickListener {
            close()
            action()
        }
        rows.addView(row)
    }

    fun close() {
        dialog?.dismiss()
    }
}
