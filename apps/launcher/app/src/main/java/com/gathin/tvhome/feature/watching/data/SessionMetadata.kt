package com.gathin.tvhome.feature.watching.data

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata

object SessionMetadata {

    fun title(metadata: MediaMetadata): String? = firstText(
        metadata,
        MediaMetadata.METADATA_KEY_TITLE,
        MediaMetadata.METADATA_KEY_DISPLAY_TITLE
    ) ?: metadata.description?.title?.toString()?.takeIf { it.isNotBlank() }

    fun subtitle(metadata: MediaMetadata): String = firstText(
        metadata,
        MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
        MediaMetadata.METADATA_KEY_ARTIST,
        MediaMetadata.METADATA_KEY_ALBUM
    ) ?: metadata.description?.subtitle?.toString().orEmpty()

    fun mediaId(metadata: MediaMetadata): String =
        firstText(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID)
            ?: metadata.description?.mediaId.orEmpty()

    fun resumeUri(metadata: MediaMetadata): String =
        firstText(metadata, MediaMetadata.METADATA_KEY_MEDIA_URI)
            ?: metadata.description?.mediaUri?.toString().orEmpty()

    fun artwork(metadata: MediaMetadata): String {
        val uri = firstText(
            metadata,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
        ) ?: metadata.description?.iconUri?.toString().orEmpty()
        return if (uri.startsWith("http")) uri else ""
    }

    fun appLabel(context: Context, packageName: String): String = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun firstText(metadata: MediaMetadata, vararg keys: String): String? {
        for (key in keys) {
            val value = metadata.getString(key)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }
}
