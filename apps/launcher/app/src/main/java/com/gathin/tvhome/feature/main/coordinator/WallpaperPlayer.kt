package com.gathin.tvhome.feature.main.coordinator

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.gathin.tvhome.feature.settings.data.Config
import com.gathin.tvhome.core.design.Palette
import com.gathin.tvhome.feature.main.data.WallpaperSize
import java.io.File
import java.util.ArrayList
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class WallpaperPlayer(
    context: Context,
    private val root: FrameLayout,
    config: Config
) {
    private val image = ImageView(context)
    private val files = ArrayList(config.wallpapers)
    private val interval: Long = config.wallpaperRotationMinutes * 60_000L
    private val preferences: SharedPreferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
    private val decoder: ExecutorService = Executors.newSingleThreadExecutor()
    private val random = Random()
    private val width: Int
    private val height: Int
    private var active = false
    private var closed = false
    private var loading = false
    private var displayed = ""

    init {
        val metrics = context.resources.displayMetrics
        width = metrics.widthPixels
        height = metrics.heightPixels
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if (config.wallpaperEnabled && files.isNotEmpty()) {
            root.addView(image, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            if (config.wallpaperDim > 0) {
                val shade = View(context).apply {
                    setBackgroundColor((config.wallpaperDim * 255 / 100) shl 24)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                root.addView(shade, 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
    }

    fun showing(): Boolean = image.drawable != null

    fun start() {
        active = true
        update()
    }

    fun stop() {
        active = false
    }

    fun release() {
        image.setImageDrawable(null)
        displayed = ""
    }

    fun close() {
        active = false
        closed = true
        decoder.shutdownNow()
    }

    private fun publishPalette(bitmap: Bitmap) {
        decoder.execute {
            val palette = paletteOf(bitmap)
            root.post {
                if (!closed && palette != null) Palette.publish(palette)
            }
        }
    }

    fun update() {
        if (!active || closed || loading || files.isEmpty()) return
        val previous = preferences.getString("image", "") ?: ""
        var selected = WallpaperRotation.find(files, previous)
        val changed = preferences.getLong("changed", 0)
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(files, previous, random)
        }
        if (selected == null || selected.name == displayed) return
        val next = selected
        loading = true
        decoder.execute {
            val bitmap = decode(next)
            val palette = paletteOf(bitmap)
            root.post {
                loading = false
                if (closed || !active) {
                    bitmap?.recycle()
                    return@post
                }
                if (bitmap == null) {
                    files.remove(next)
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.name)
                    update()
                    return@post
                }
                image.setImageBitmap(bitmap)
                if (palette != null) Palette.publish(palette)
                displayed = next.name
                if (displayed != previous || changed == 0L) {
                    preferences.edit().putString("image", displayed)
                        .putLong("changed", System.currentTimeMillis()).apply()
                }
            }
        }
    }

    private fun decode(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.path, options)
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null
            val sample = WallpaperSize.sampleSize(w, h, width, height)
            if (!WallpaperSize.accepted(w / sample, h / sample)) return null
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            val scale = WallpaperSize.coverScale(w / sample, h / sample, width, height)
            if (scale != null) {
                options.inScaled = true
                options.inDensity = scale[0]
                options.inTargetDensity = scale[1]
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565
            BitmapFactory.decodeFile(file.path, options)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    companion object {
        private fun paletteOf(bitmap: Bitmap?): Palette? {
            if (bitmap == null) return null
            return try {
                Palette.fromWallpaper(bitmap)
            } catch (failure: Exception) {
                Log.w("TvHome.Wallpaper", "No dynamic colour: $failure")
                null
            } catch (failure: OutOfMemoryError) {
                Log.w("TvHome.Wallpaper", "No dynamic colour: $failure")
                null
            }
        }
    }
}
