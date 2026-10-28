package com.gathin.tvhome.feature.watching.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.net.Uri
import java.io.File

class ArtworkStore(context: Context) {

    private val folder = File(context.filesDir, FOLDER)

    fun artwork(metadata: MediaMetadata, key: String): String {
        val remote = SessionMetadata.artwork(metadata)
        if (remote.isNotEmpty()) return remote

        val file = File(folder, "${Integer.toHexString(key.hashCode())}.jpg")
        if (file.isFile) return Uri.fromFile(file).toString()
        val bitmap = bitmap(metadata) ?: return ""
        return if (save(bitmap, file)) Uri.fromFile(file).toString() else ""
    }

    private fun bitmap(metadata: MediaMetadata): Bitmap? {
        for (key in BITMAP_KEYS) metadata.getBitmap(key)?.let { return it }
        return metadata.description?.iconBitmap
    }

    private fun save(bitmap: Bitmap, file: File): Boolean = try {
        folder.mkdirs()
        prune()
        val scaled = scale(bitmap)
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        true
    } catch (_: Exception) {
        file.delete()
        false
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_WIDTH) return bitmap
        val height = bitmap.height * MAX_WIDTH / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, height, true)
    }

    private fun prune() {
        val files = folder.listFiles() ?: return
        if (files.size < MAX_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_FILES + 1)
            .forEach { it.delete() }
    }

    private companion object {
        const val FOLDER = "watch_artwork"
        const val MAX_FILES = 24
        const val MAX_WIDTH = 480
        const val QUALITY = 85
        val BITMAP_KEYS = arrayOf(
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
        )
    }
}
