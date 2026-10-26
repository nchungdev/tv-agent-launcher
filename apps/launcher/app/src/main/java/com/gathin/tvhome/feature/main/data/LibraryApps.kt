package com.gathin.tvhome.feature.main.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.TreeSet
import java.util.function.IntPredicate


object LibraryApps {
    const val MAX_ADDED = 128
    private const val RECENT_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_BYTES = MAX_ADDED * 256

    private fun store(context: Context, removed: Boolean): AtomicFile {
        return AtomicFile(File(context.filesDir, if (removed) "library_home_removed.txt" else "library_apps.txt"))
    }

    @JvmStatic
    fun load(context: Context): Set<String> = read(store(context, false))

    @JvmStatic
    fun loadRemoved(context: Context): Set<String> = read(store(context, true))

    private fun read(file: AtomicFile): Set<String> {
        return try {
            readStrict(file)
        } catch (e: Exception) {
            Log.w("TvHome.Library", "Could not read shortcuts", e)
            emptySet()
        }
    }

    @Throws(IOException::class)
    private fun readStrict(file: AtomicFile): Set<String> {
        val baseFile = file.baseFile
        if (!baseFile.exists() && !File(baseFile.path + ".bak").exists()) {
            return emptySet()
        }
        file.openRead().use { input ->
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var count: Int
            while (input.read(buffer).also { count = it } != -1) {
                if (bytes.size() + count > MAX_BYTES) throw IOException("Shortcut file too large")
                bytes.write(buffer, 0, count)
            }
            return decode(String(bytes.toByteArray(), StandardCharsets.UTF_8))
        }
    }

    @JvmStatic
    fun save(context: Context, pkg: String, onHome: Boolean): Boolean {
        val additions: Set<String>
        val removed: Set<String>
        try {
            additions = readStrict(store(context, false))
            removed = readStrict(store(context, true))
        } catch (e: Exception) {
            Log.w("TvHome.Library", "Keeping unreadable shortcut choices unchanged", e)
            return false
        }
        if (onHome) {
            if (!write(store(context, false), additions, pkg, true)) return false
            return !removed.contains(pkg) || write(store(context, true), removed, pkg, false)
        }
        if (!write(store(context, true), removed, pkg, true)) return false
        if (additions.contains(pkg)) write(store(context, false), additions, pkg, false)
        return true
    }

    private fun write(file: AtomicFile, saved: Set<String>, pkg: String, added: Boolean): Boolean {
        var output: java.io.FileOutputStream? = null
        return try {
            val next = change(saved, pkg, added)
            val bytes = encode(next).toByteArray(StandardCharsets.UTF_8)
            output = file.startWrite()
            output.write(bytes)
            file.finishWrite(output)
            true
        } catch (e: Exception) {
            if (output != null) file.failWrite(output)
            Log.w("TvHome.Library", "Could not save shortcut choice", e)
            false
        }
    }

    @JvmStatic
    @JvmOverloads
    fun visible(
        tvApp: Boolean,
        themeHidden: Boolean,
        explicitlyAdded: Boolean,
        removedFromHome: Boolean = false
    ): Boolean {
        return !removedFromHome && (explicitlyAdded || (tvApp && !themeHidden))
    }

    @JvmStatic
    fun recent(installedAt: Long, now: Long): Boolean {
        return installedAt > 0 && installedAt <= now && now - installedAt <= RECENT_MS
    }

    @JvmStatic
    fun comparePriority(homeA: Boolean, installedA: Long, homeB: Boolean, installedB: Long, now: Long): Int {
        if (homeA != homeB) return if (homeA) 1 else -1
        if (homeA) return 0
        val recentA = recent(installedA, now)
        val recentB = recent(installedB, now)
        if (recentA != recentB) return if (recentA) -1 else 1
        return if (recentA) installedB.compareTo(installedA) else 0
    }

    @JvmStatic
    fun nextFocus(current: Int, count: Int, missing: IntPredicate): Int {
        if (count <= 0) return -1
        val pos = current.coerceIn(0, count - 1)
        for (step in 1 until count) {
            val candidate = (pos + step) % count
            if (missing.test(candidate)) return candidate
        }
        return if (pos + 1 < count) pos + 1 else maxOf(0, pos - 1)
    }

    @JvmStatic
    fun change(saved: Set<String>, pkg: String, added: Boolean): Set<String> {
        if (!validPackage(pkg)) throw IllegalArgumentException("Invalid package")
        val next = HashSet(saved)
        if (added) next.add(pkg) else next.remove(pkg)
        if (next.size > MAX_ADDED) throw IllegalArgumentException("Too many shortcuts")
        return next
    }

    @JvmStatic
    fun encode(added: Set<String>): String {
        if (added.size > MAX_ADDED) throw IllegalArgumentException("Too many shortcuts")
        val text = StringBuilder()
        for (pkg in TreeSet(added)) {
            if (!validPackage(pkg)) throw IllegalArgumentException("Invalid package")
            text.append(pkg).append('\n')
        }
        return text.toString()
    }

    @JvmStatic
    fun decode(text: String): Set<String> {
        if (text.length > MAX_BYTES) throw IllegalArgumentException("Shortcut file too large")
        val result = HashSet<String>()
        var start = 0
        for (i in 0..text.length) {
            if (i < text.length && text[i] != '\n') continue
            if (i == start && i == text.length) break
            val pkg = text.substring(start, i)
            if (!validPackage(pkg) || !result.add(pkg) || result.size > MAX_ADDED) {
                throw IllegalArgumentException("Invalid shortcut file")
            }
            start = i + 1
        }
        return result
    }

    @JvmStatic
    fun validPackage(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty() || pkg.length > 255) return false
        var first = true
        for (i in pkg.indices) {
            val c = pkg[i]
            if (c == '.') {
                if (first) return false
                first = true
            } else {
                val letter = c in 'a'..'z' || c in 'A'..'Z' || c == '_'
                if (!letter && (first || c < '0' || c > '9')) return false
                first = false
            }
        }
        return !first
    }
}
