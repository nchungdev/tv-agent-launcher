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

    fun details(
        packageName: String, metadata: MediaMetadata, title: String, subtitle: String
    ): WatchDetails {
        if (packageName in VIDEO_APPS) {
            val channel = firstText(metadata, MediaMetadata.METADATA_KEY_ARTIST) ?: subtitle
            return WatchDetails(kind = WatchDetails.VIDEO, channel = channel)
        }
        return WatchDetails.guess("$title $subtitle", year(metadata))
    }

    private fun year(metadata: MediaMetadata): Int {
        val year = metadata.getLong(MediaMetadata.METADATA_KEY_YEAR).toInt()
        if (year > 0) return year
        val date = firstText(metadata, MediaMetadata.METADATA_KEY_DATE).orEmpty()
        return YEAR.find(date)?.value?.toIntOrNull() ?: 0
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

    private val YEAR = Regex("""(19|20)\d{2}""")
    private val VIDEO_APPS = setOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "com.liskovsoft.smarttubetv.beta",
        "com.teamsmart.videomanager.tv"
    )
}
