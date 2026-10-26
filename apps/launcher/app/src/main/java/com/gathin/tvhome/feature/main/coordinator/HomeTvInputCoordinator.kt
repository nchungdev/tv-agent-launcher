package com.gathin.tvhome.feature.main.coordinator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.system.data.TvSettings
import com.gathin.tvhome.feature.system.data.XiaomiInputs
import java.util.Locale


class HomeTvInputCoordinator(
    private val activity: Activity,
    private val onLaunchIntent: (Intent) -> Unit
) {
    private var inputsPopup: PopupWindow? = null

    fun showInputs(anchor: View, root: ViewGroup) {
        if (inputsPopup?.isShowing == true) {
            inputsPopup?.dismiss()
            return
        }
        val density = activity.resources.displayMetrics.density
        val inflater = LayoutInflater.from(activity)
        val panel = inflater.inflate(R.layout.popup_inputs, root, false)
        val rows = panel.findViewById<LinearLayout>(R.id.rows)
        val width = maxOf(anchor.width, (150 * density).toInt())
        val popup = PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }
        inputsPopup = popup

        val tim = activity.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

        if (tim != null) {
            val list = ArrayList(tim.tvInputList)
            list.sortWith(Comparator { first, second ->
                val firstHdmi = first.type == TvInputInfo.TYPE_HDMI
                val secondHdmi = second.type == TvInputInfo.TYPE_HDMI
                if (firstHdmi != secondHdmi) return@Comparator if (firstHdmi) -1 else 1
                val firstLabel = first.loadLabel(activity).toString()
                val secondLabel = second.loadLabel(activity).toString()
                firstLabel.compareTo(secondLabel, ignoreCase = true)
            })

            for (info in list) {
                if (!info.isPassthroughInput || info.isHidden(activity)) continue
                var label = info.loadLabel(activity)?.toString() ?: info.id
                val isHdmi = info.type == TvInputInfo.TYPE_HDMI
                if (isHdmi && !label.uppercase(Locale.ROOT).contains("HDMI")) {
                    label = "HDMI $label"
                }
                val iconRes = if (isHdmi) R.drawable.ic_input else R.drawable.ic_av
                val state = tim.getInputState(info.id)
                addInputRow(rows, popup, iconRes, label, state) {
                    val xiomiSource = XiaomiInputs.source(label)
                    val intent = if (xiomiSource >= 0) {
                        XiaomiInputs.intent(xiomiSource)
                    } else {
                        Intent(
                            Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(info.id)
                        )
                    }
                    intent.let { onLaunchIntent(it) }
                }
            }
        }

        if (rows.childCount == 0) {
            Toast.makeText(activity, R.string.no_inputs, Toast.LENGTH_SHORT).show()
            return
        }
        popup.showAsDropDown(anchor, 0, 0, Gravity.START)
    }

    private fun addInputRow(
        parent: LinearLayout,
        popup: PopupWindow,
        iconRes: Int,
        label: String,
        state: Int,
        onClick: () -> Unit
    ) {
        val row = LayoutInflater.from(activity).inflate(R.layout.item_input, parent, false)
        row.findViewById<ImageView>(R.id.icon)?.setImageResource(iconRes)
        row.findViewById<TextView>(R.id.title)?.text = label
        val dot = row.findViewById<View>(R.id.dot)
        val status = row.findViewById<TextView>(R.id.status)
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status?.setText(R.string.connected)
            dot?.visibility = View.VISIBLE
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status?.setText(R.string.not_connected)
            dot?.visibility = View.GONE
        } else {
            status?.visibility = View.GONE
            dot?.visibility = View.GONE
        }
        row.setOnClickListener {
            popup.dismiss()
            onClick()
        }
        parent.addView(row)
    }
}
