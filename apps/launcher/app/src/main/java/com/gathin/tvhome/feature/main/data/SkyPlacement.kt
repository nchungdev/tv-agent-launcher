package com.gathin.tvhome.feature.main.data

import kotlin.math.min

object SkyPlacement {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val cx get() = (left + right) / 2
        val cy get() = (top + bottom) / 2
        fun overlaps(other: Box): Boolean = left < other.right && right > other.left &&
            top < other.bottom && bottom > other.top
        fun padded(gap: Float) = Box(left - gap, top - gap, right + gap, bottom + gap)
    }

    data class Layout(val celestial: Box?, val clouds: List<Box>)

    fun arrange(area: Box, exclusions: List<Box>, clouds: Int, density: Float): Layout {
        if (area.width <= 0 || area.height <= 0) return Layout(null, emptyList())
        val occupied = exclusions.toMutableList()
        val diameter = min(area.height * 0.32f, 100 * density)
        val celestial = find(area, occupied, diameter, diameter, 0.65f, 0.3f, 32 * density)
        if (celestial != null) occupied.add(celestial.padded(10 * density))
        val layers = ArrayList<Box>(3)
        if (celestial != null && clouds > 0) {
            val cover = Box(celestial.cx - celestial.width * 0.12f,
                celestial.cy + celestial.height * 0.06f,
                celestial.right + celestial.width * 0.45f,
                celestial.bottom + celestial.height * 0.1f)
            if (cover.right <= area.right && cover.bottom <= area.bottom &&
                exclusions.none { cover.overlaps(it) }) {
                layers.add(cover)
                occupied.add(cover.padded(4 * density))
            }
        }
        repeat((clouds - layers.size).coerceIn(0, 3)) { index ->
            val width = min(area.width * (0.27f - index * 0.035f), 240 * density)
            val height = width * 0.45f
            val cloud = find(
                area, occupied, width, height, if (index % 2 == 0) 0.8f else 0.3f,
                0.6f + index * 0.12f, 56 * density
            )
            if (cloud != null) {
                layers.add(cloud)
                occupied.add(cloud.padded(4 * density))
            }
        }
        return Layout(celestial, layers)
    }

    private fun find(
        area: Box, occupied: List<Box>, width: Float, height: Float,
        preferX: Float, preferY: Float, minimum: Float
    ): Box? {
        for (scale in SCALES) {
            val w = width * scale
            val h = height * scale
            if (w < minimum || w > area.width || h > area.height) continue
            var best: Box? = null
            var score = Float.MAX_VALUE
            for (row in 0..12) for (column in 0..20) {
                val x = column / 20f
                val y = row / 12f
                val left = area.left + (area.width - w) * x
                val top = area.top + (area.height - h) * y
                val distance = (x - preferX) * (x - preferX) + (y - preferY) * (y - preferY)
                if (distance >= score || occupied.any {
                    left < it.right && left + w > it.left && top < it.bottom && top + h > it.top
                }) continue
                if (distance < score) {
                    best = Box(left, top, left + w, top + h)
                    score = distance
                }
            }
            if (best != null) return best
        }
        return null
    }

    private val SCALES = floatArrayOf(1f, 0.8f, 0.6f)
}
