package com.gathin.tvhome.feature.watching.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.net.Uri
import java.io.File
import java.io.ByteArrayOutputStream
import android.util.AtomicFile

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

    private fun save(bitmap: Bitmap, file: File): Boolean {
        var scaled: Bitmap? = null
        return try {
            folder.mkdirs()
            prune()
            val ratio = minOf(1f, MAX_WIDTH.toFloat() / maxOf(bitmap.width, bitmap.height))
            scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap,
                maxOf(1, (bitmap.width * ratio).toInt()),
                maxOf(1, (bitmap.height * ratio).toInt()), true) else bitmap
            val bytes = ByteArrayOutputStream()
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, bytes) ||
                bytes.size() > 256 * 1024) return false
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try {
                stream.write(bytes.toByteArray())
                atomic.finishWrite(stream)
            } catch (error: Exception) {
                atomic.failWrite(stream)
                throw error
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            if (scaled !== bitmap) scaled?.recycle()
        }
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
