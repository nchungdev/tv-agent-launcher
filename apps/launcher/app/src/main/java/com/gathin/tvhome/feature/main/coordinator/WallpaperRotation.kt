package com.gathin.tvhome.feature.main.coordinator

import com.gathin.tvhome.Contract
import java.io.File
import java.util.Locale
import java.util.Random

object WallpaperRotation {

    @JvmStatic
    fun images(directory: File?): List<File> {
        val result = ArrayList<File>()
        val entries = directory?.listFiles() ?: return result
        entries.sortBy { it.name }
        for (entry in entries) {
            val name = entry.name.lowercase(Locale.ROOT)
            if (entry.isFile && entry.length() > 0 && entry.length() <= Contract.Theme.MAX_WALLPAPER_BYTES && hasImageExtension(name)) {
                result.add(entry)
                if (result.size == Contract.Theme.MAX_WALLPAPERS) break
            }
        }
        return result
    }

    private fun hasImageExtension(name: String): Boolean {
        for (extension in Contract.Theme.IMAGE_EXTENSIONS) {
            if (name.endsWith(extension)) return true
        }
        return false
    }

    @JvmStatic
    fun find(images: List<File>, name: String): File? {
        return images.find { it.name == name }
    }

    @JvmStatic
    fun pick(images: List<File>, previous: String?, random: Random): File? {
        val candidates = ArrayList(images)
        if (candidates.size > 1 && previous != null) {
            candidates.removeAll { it.name == previous }
        }
        return if (candidates.isEmpty()) null else candidates[random.nextInt(candidates.size)]
    }

    @JvmStatic
    fun due(previous: Long, now: Long, interval: Long): Boolean {
        return interval > 0 && (previous == 0L || now < previous || now - previous >= interval)
    }
}
