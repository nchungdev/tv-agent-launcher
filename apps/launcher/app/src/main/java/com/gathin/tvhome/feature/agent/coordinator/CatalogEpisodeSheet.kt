package com.gathin.tvhome.feature.agent.coordinator

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import android.widget.ProgressBar
import android.widget.Toast
import com.gathin.tvhome.R
import com.gathin.tvhome.feature.agent.data.CatalogClient
import com.gathin.tvhome.feature.agent.data.CatalogTitle
import com.gathin.tvhome.feature.player.PickerDialog
import com.gathin.tvhome.feature.player.PlayRequest
import com.gathin.tvhome.feature.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CatalogEpisodeSheet {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @JvmStatic
    fun show(context: Context, titleId: String) {
        val loading = loadingDialog(context)
        scope.launch {
            val title = withContext(Dispatchers.IO) {
                runCatching { CatalogClient.getTitle(context, titleId) }.getOrNull()
            }
            loading.dismiss()
            if (title == null) {
                Toast.makeText(context, R.string.catalog_load_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            open(context, title)
        }
    }

    private fun open(context: Context, title: CatalogTitle) {
        val playable = title.episodes.filter { it.playable }
        val first = playable.minByOrNull { it.order }
        if (first == null) {
            Toast.makeText(context, R.string.catalog_no_episodes, Toast.LENGTH_LONG).show()
            return
        }
        val request = PlayRequest.of(title.copy(episodes = playable), first)
        if (!request.series && request.servers.size < 2) {
            PlayerActivity.start(context, request)
            return
        }
        PickerDialog.show(context, request) { episode ->
            PlayerActivity.start(context, request.with(episode))
        }
    }

    private fun loadingDialog(context: Context): Dialog {
        val density = context.resources.displayMetrics.density
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(ProgressBar(context))
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(0))
            val size = (72 * density).toInt()
            window.setLayout(size, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setDimAmount(DIM)
        }
        dialog.show()
        return dialog
    }

    private const val DIM = 0.4f
}
