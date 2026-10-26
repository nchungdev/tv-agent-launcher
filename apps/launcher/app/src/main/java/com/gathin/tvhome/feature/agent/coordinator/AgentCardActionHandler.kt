package com.gathin.tvhome.feature.agent.coordinator
import com.gathin.tvhome.core.design.Fonts
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.ui.*
import com.gathin.tvhome.feature.agent.widget.*

import com.gathin.tvhome.core.view.AgentCardData
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.AgentClient
import com.gathin.tvhome.feature.agent.data.InputEvent
import com.gathin.tvhome.feature.agent.data.PresentationResponse
import com.gathin.tvhome.core.design.M3Skin
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.core.design.Shapes
import com.gathin.tvhome.feature.agent.coordinator.AgentActionExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


object AgentCardActionHandler {

    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun handleCardAction(context: Context, card: AgentCardData) {
        if (card.status.equals("plex", ignoreCase = true)) {
            openPlex(context, card.link)
            return
        }
        if (card.status.equals("downloading", ignoreCase = true)) {
            showDownloadingDialog(context, card.title)
            return
        }
        showAddMediaDialog(context, card.title, card.type)
    }

    private fun openPlex(context: Context, uriString: String) {
        val params = if (uriString.isBlank()) emptyMap() else mapOf("uri" to uriString)
        AgentActionExecutor.execute(context, "plex_open", "plex.open", params)
    }

    private fun showDownloadingDialog(context: Context, title: String) {
        val density = context.resources.displayMetrics.density
        val palette = Palette.currentOrBaseline()

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(palette.surfaceContainerHigh)
                cornerRadius = Shapes.EXTRA_LARGE * density
            }
            elevation = 12 * density
        }

        val titleView = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(palette.onSurface)
            typeface = Fonts.MEDIUM
        }
        panel.addView(titleView)

        val messageView = TextView(context).apply {
            text = "Phim/series này đang trong hàng đợi tải về trên NAS."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.onSurfaceVariant)
            typeface = Fonts.REGULAR
            setLineSpacing(0f, 1.25f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * density).toInt()
            }
        }
        panel.addView(messageView)

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (24 * density).toInt()
            }
        }

        val btnClose = TextView(context).apply {
            text = "Đóng"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            tag = "m3:button"
            isFocusable = true
            isClickable = true
            val horizontal = (20 * density).toInt()
            val vertical = (10 * density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
        }

        buttonRow.addView(btnClose)
        panel.addView(buttonRow)

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(panel)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(0))
            window.setLayout((420 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(0.6f)
        }

        M3Skin.apply(panel, palette, true, false)
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
        btnClose.requestFocus()
    }

    private fun showAddMediaDialog(context: Context, title: String, type: String) {
        val density = context.resources.displayMetrics.density
        val palette = Palette.currentOrBaseline()

        val isSeries = type.equals("Series", ignoreCase = true)
        val typeLabel = if (isSeries) "series" else "phim lẻ"
        val prompt = "Bạn có muốn thêm $typeLabel \"$title\" vào thư viện để NAS tự tìm kiếm không?"

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(palette.surfaceContainerHigh)
                cornerRadius = Shapes.EXTRA_LARGE * density
            }
            elevation = 12 * density
        }

        val titleView = TextView(context).apply {
            text = "Thêm vào thư viện"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(palette.onSurface)
            typeface = Fonts.MEDIUM
        }
        panel.addView(titleView)

        val messageView = TextView(context).apply {
            text = prompt
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.onSurfaceVariant)
            typeface = Fonts.REGULAR
            setLineSpacing(0f, 1.25f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * density).toInt()
            }
        }
        panel.addView(messageView)

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (24 * density).toInt()
            }
        }

        val btnCancel = TextView(context).apply {
            text = "Hủy"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            tag = "m3:button"
            isFocusable = true
            isClickable = true
            val horizontal = (20 * density).toInt()
            val vertical = (10 * density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
        }

        val btnAdd = TextView(context).apply {
            text = "Thêm vào NAS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            tag = "m3:button"
            isFocusable = true
            isClickable = true
            val horizontal = (20 * density).toInt()
            val vertical = (10 * density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (10 * density).toInt()
            }
        }

        buttonRow.addView(btnCancel)
        buttonRow.addView(btnAdd)
        panel.addView(buttonRow)

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(panel)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(0))
            window.setLayout((450 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(0.6f)
        }

        M3Skin.apply(panel, palette, true, false)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnAdd.setOnClickListener {
            dialog.dismiss()
            requestAddMedia(context, title, type)
        }
        dialog.show()
        btnAdd.requestFocus()
    }

    private fun requestAddMedia(context: Context, title: String, type: String) {
        Toast.makeText(context, "Đang gửi yêu cầu nạp tải...", Toast.LENGTH_SHORT).show()
        actionScope.launch {
            val isSeries = type.equals("Series", ignoreCase = true)
            val prompt = (if (isSeries) "Nạp series " else "Nạp phim ") + title
            withContext(Dispatchers.IO) {
                try {
                    val event = InputEvent(
                        requestId = "req-media-" + System.currentTimeMillis(),
                        sessionId = "add_media",
                        text = prompt,
                        locale = "vi-VN",
                        inputType = "click"
                    )
                    AgentClient.askV2(context, event, object : AgentClient.V2StreamListener {
                        override fun onPresentation(presentation: PresentationResponse) {
                            actionScope.launch {
                                val msg = "Đã thêm thành công vào NAS!"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }

                        override fun onError(code: String, message: String) {
                            actionScope.launch {
                                val msg = "Lỗi máy chủ ($code)"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    })
                } catch (error: Exception) {
                    actionScope.launch {
                        val msg = "Lỗi kết nối: ${error.message}"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
