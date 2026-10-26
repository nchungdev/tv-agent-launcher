package com.gathin.tvhome.feature.main.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets


class Order(
    @JvmField val dock: String,
    keys: List<String>
) {
    @JvmField
    val keys: List<String> = keys.toList()

    companion object {
        private const val TAG = "TvHome.Order"
        const val FILE_NAME = "order.txt"
        private const val MAGIC = "tvhome-order 1"
        private const val DOCK = "dock "

        @JvmStatic
        fun dockKey(dockApps: List<String>): String = dockApps.joinToString(",")

        @JvmStatic
        fun encode(order: Order): String {
            val sb = StringBuilder(MAGIC).append('\n').append(DOCK).append(order.dock).append('\n')
            for (k in order.keys) sb.append(k).append('\n')
            return sb.toString()
        }

        @JvmStatic
        fun decode(text: String?): Order? {
            if (text == null) return null
            val lines = text.split("\n")
            if (lines.size < 2 || lines[0] != MAGIC || !lines[1].startsWith(DOCK)) return null
            val keys = ArrayList<String>()
            for (i in 2 until lines.size) {
                val k = lines[i]
                if (k.isEmpty()) {
                    if (i == lines.size - 1) break
                    return null
                }
                if (keys.contains(k)) return null
                keys.add(k)
            }
            return Order(lines[1].substring(DOCK.length), keys)
        }

        @JvmStatic
        fun ranks(order: Order?, dockApps: List<String>): Map<String, Int> {
            val r = HashMap<String, Int>()
            if (order == null || order.dock != dockKey(dockApps)) return r
            for (i in order.keys.indices) r[order.keys[i]] = i
            return r
        }

        @JvmStatic
        fun compare(ranks: Map<String, Int>, keyA: String, keyB: String): Int {
            val a = ranks[keyA]
            val b = ranks[keyB]
            if (a == null && b == null) return 0
            if (a == null) return 1
            if (b == null) return -1
            return a.compareTo(b)
        }

        @JvmStatic
        fun file(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

        @JvmStatic
        fun load(file: File): Order? {
            if (!file.exists()) return null
            return try {
                val o = decode(String(AtomicFile(file).readFully(), StandardCharsets.UTF_8))
                if (o == null) Log.w(TAG, "${file.name} is not valid; ignored")
                o
            } catch (e: IOException) {
                Log.w(TAG, "cannot read ${file.name}: $e")
                null
            }
        }

        @JvmStatic
        fun save(file: File, order: Order): Boolean {
            val af = AtomicFile(file)
            var out: java.io.FileOutputStream? = null
            return try {
                out = af.startWrite()
                out.write(encode(order).toByteArray(StandardCharsets.UTF_8))
                af.finishWrite(out)
                true
            } catch (e: IOException) {
                Log.w(TAG, "cannot write ${file.name}: $e")
                if (out != null) af.failWrite(out)
                false
            }
        }
    }
}
