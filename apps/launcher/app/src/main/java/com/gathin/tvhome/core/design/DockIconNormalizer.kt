package com.gathin.tvhome.core.design

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable

object DockIconNormalizer {

    data class Result(val drawable: Drawable, val fullBleed: Boolean, val edgeColor: Int)

    private const val CANVAS_SIZE = 128
    private const val ALPHA_THRESHOLD = 210

    fun normalize(resources: Resources, icon: Drawable?): Result? {
        if (icon == null) return null
        if (icon is AdaptiveIconDrawable) {
            return normalizeAdaptive(resources, icon)
        }
        return normalizeBitmap(resources, icon)
    }

    private fun normalizeAdaptive(resources: Resources, icon: AdaptiveIconDrawable): Result {
        val bg = icon.background ?: ColorDrawable(Color.BLACK)
        val fg = icon.foreground ?: ColorDrawable(Color.TRANSPARENT)
        val layers = LayerDrawable(arrayOf(bg, fg))
        val inset = -Math.round(CANVAS_SIZE * AdaptiveIconDrawable.getExtraInsetFraction())
        layers.setLayerInset(0, inset, inset, inset, inset)
        layers.setLayerInset(1, inset, inset, inset, inset)
        val bmp = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        layers.setBounds(0, 0, CANVAS_SIZE, CANVAS_SIZE)
        layers.draw(canvas)
        val edgeColor = bmp.getPixel(CANVAS_SIZE / 2, 4)
        return Result(BitmapDrawable(resources, bmp), true, edgeColor)
    }

    private fun normalizeBitmap(resources: Resources, icon: Drawable): Result {
        val bmp = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        icon.setBounds(0, 0, CANVAS_SIZE, CANVAS_SIZE)
        icon.draw(canvas)

        val bounds = findOpaqueBounds(bmp) ?: return Result(icon, false, Color.TRANSPARENT)
        val left = bounds[0]
        val top = bounds[1]
        val right = bounds[2]
        val bottom = bounds[3]
        val width = right - left + 1
        val height = bottom - top + 1
        if (width < CANVAS_SIZE / 2 || height < CANVAS_SIZE / 2) {
            return Result(icon, false, Color.TRANSPARENT)
        }

        if (!isFilledTile(bmp, left, top, right, bottom)) {
            return Result(icon, false, Color.TRANSPARENT)
        }

        val inset = 2
        val cropLeft = (left + inset).coerceAtMost(right)
        val cropTop = (top + inset).coerceAtMost(bottom)
        val cropWidth = (width - inset * 2).coerceAtLeast(1)
        val cropHeight = (height - inset * 2).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(bmp, cropLeft, cropTop, cropWidth, cropHeight)
        val edgeColor = cropped.getPixel(cropped.width / 2, minOf(3, cropped.height - 1))
        return Result(BitmapDrawable(resources, cropped), true, edgeColor)
    }

    private fun findOpaqueBounds(bmp: Bitmap): IntArray? {
        var minX = CANVAS_SIZE
        var minY = CANVAS_SIZE
        var maxX = -1
        var maxY = -1
        for (y in 0 until CANVAS_SIZE) {
            for (x in 0 until CANVAS_SIZE) {
                if (Color.alpha(bmp.getPixel(x, y)) >= ALPHA_THRESHOLD) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return null
        return intArrayOf(minX, minY, maxX, maxY)
    }

    private fun isFilledTile(bmp: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        val width = right - left
        val height = bottom - top
        val diagX = maxOf(4, width * 12 / 100)
        val diagY = maxOf(4, height * 12 / 100)
        val points = arrayOf(
            intArrayOf(left + diagX, top + diagY),
            intArrayOf(right - diagX, top + diagY),
            intArrayOf(left + diagX, bottom - diagY),
            intArrayOf(right - diagX, bottom - diagY),
            intArrayOf(left + width / 2, top + 4),
            intArrayOf(left + width / 2, bottom - 4),
            intArrayOf(left + 4, top + height / 2),
            intArrayOf(right - 4, top + height / 2)
        )
        for (pt in points) {
            val x = pt[0].coerceIn(0, CANVAS_SIZE - 1)
            val y = pt[1].coerceIn(0, CANVAS_SIZE - 1)
            if (Color.alpha(bmp.getPixel(x, y)) < ALPHA_THRESHOLD) return false
        }
        return true
    }
}
