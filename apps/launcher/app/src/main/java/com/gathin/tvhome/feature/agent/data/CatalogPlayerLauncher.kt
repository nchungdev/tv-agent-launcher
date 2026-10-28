package com.gathin.tvhome.feature.agent.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object CatalogPlayerLauncher {

    const val VLC_PACKAGE = "org.videolan.vlc"
    const val JUST_PLAYER_PACKAGE = "com.brouken.player"
    const val MX_PLAYER_PACKAGE = "com.mxtech.videoplayer.ad"
    const val MX_PLAYER_PRO_PACKAGE = "com.mxtech.videoplayer.pro"

    @JvmStatic
    fun play(context: Context, playback: PlaybackDescriptor, title: String = "") {
        val url = playback.url.trim()
        if (url.isBlank()) {
            Toast.makeText(context, "URL luồng phát không hợp lệ", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse(url)
        val mimeType = when {
            playback.mimeType.isNotBlank() -> playback.mimeType
            url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            url.contains(".mp4", ignoreCase = true) -> "video/mp4"
            else -> "video/*"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (title.isNotBlank()) {
                putExtra("title", title)
                putExtra("android.media.intent.extra.TITLE", title)
            }
        }

        val pm = context.packageManager
        val candidates = listOf(VLC_PACKAGE, JUST_PLAYER_PACKAGE, MX_PLAYER_PACKAGE, MX_PLAYER_PRO_PACKAGE)
        val targetPackage = candidates.firstOrNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }

        if (targetPackage != null) {
            intent.setPackage(targetPackage)
        }

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            intent.setPackage(null)
            try {
                val chooser = Intent.createChooser(intent, "Chọn trình phát video").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (_: Exception) {
                Toast.makeText(context, "Cần cài đặt ứng dụng phát video (VLC, Just Player, v.v.)", Toast.LENGTH_LONG).show()
            }
        }
    }
}
