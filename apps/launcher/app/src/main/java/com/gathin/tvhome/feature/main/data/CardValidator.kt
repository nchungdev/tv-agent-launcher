package com.gathin.tvhome.feature.main.data

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.TreeMap


object CardValidator {

    private const val TEXT_PUNCTUATION = ".&+'-"
    private val RESERVED_ANYWHERE = arrayOf("verified", "official")
    private val RESERVED_EXACT = setOf("tvvibehack", "cleanyourtv", "settings", "inputs", "livetv", "home", "defaulthome", "wifi", "av")
    private const val RESERVED_PREFIX = "hdmi"

    private const val HEADER = "{\"version\":2,\"records\":["
    private const val FOOTER = "]}"
    private const val F_PACKAGE = "{\"package\":\""
    private const val F_DIGEST = "\",\"signer_sha256\":\""
    private const val F_LABEL = "\",\"label\":\""
    private const val F_DECISION = "\",\"decision\":\""
    private const val F_FIRST_SEEN = "\",\"first_seen\":"
    private const val F_DECIDED_AT = ",\"decided_at\":"

    private var selfChecked = false

    @JvmStatic
    fun checkLabel(label: String?): String? {
        if (label.isNullOrEmpty()) return "label is missing"
        if (label.length > Cards.LABEL_MAX) return "label is longer than ${Cards.LABEL_MAX} characters"
        if (isReference(label)) return "label is a resource reference; write the text itself"
        if (!isText(label)) return "label has characters outside A-Z a-z 0-9 space . & + ' -"
        if (label.first() == ' ' || label.last() == ' ') return "label starts or ends with a space"
        if (fold(label).isEmpty()) return "label has no letter or digit"
        return null
    }

    @JvmStatic
    fun checkCaption(caption: String?): String? {
        if (caption.isNullOrEmpty()) return null
        if (caption.length > Cards.CAPTION_MAX) return "caption is longer than ${Cards.CAPTION_MAX} characters"
        if (isReference(caption)) return "caption is a resource reference; write the text itself"
        if (!isText(caption)) return "caption has characters outside A-Z a-z 0-9 space . & + ' -"
        if (caption.first() == ' ' || caption.last() == ' ') return "caption starts or ends with a space"
        val folded = fold(caption)
        for (word in RESERVED_ANYWHERE) {
            if (folded.contains(word)) return "caption claims endorsement ('$word')"
        }
        return null
    }

    @JvmStatic
    fun cleanLabel(s: String?, fallback: String): String {
        if (s == null) return fallback
        val b = StringBuilder(minOf(s.length, 48))
        var space = true
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val type = Character.getType(c)
            val blank = c == ' ' || Character.isISOControl(c) || Character.isWhitespace(c) ||
                    type == Character.FORMAT.toInt() || type == Character.LINE_SEPARATOR.toInt() ||
                    type == Character.PARAGRAPH_SEPARATOR.toInt() || type == Character.SPACE_SEPARATOR.toInt()
            if (blank) {
                if (!space) b.append(' ')
                space = true
                i++
                continue
            }
            val pair = Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s[i + 1])
            if (b.length + (if (pair) 2 else 1) > 40) break
            b.append(c)
            if (pair) b.append(s[++i])
            space = false
            i++
        }
        val out = b.toString().trim()
        return if (out.isEmpty()) fallback else out
    }

    @JvmStatic
    fun isReference(value: String): Boolean = value.startsWith("@") || value.startsWith("?")

    @JvmStatic
    fun fold(s: String?): String {
        if (s == null) return ""
        val b = StringBuilder(s.length)
        for (i in s.indices) {
            val c = s[i].lowercaseChar()
            if (c in 'a'..'z' || c in '0'..'9') b.append(c)
        }
        return b.toString()
    }

    @JvmStatic
    fun isReserved(folded: String, uiNames: Set<String>): Boolean {
        if (folded.isEmpty()) return true
        for (word in RESERVED_ANYWHERE) if (folded.contains(word)) return true
        if (folded.startsWith(RESERVED_PREFIX)) return true
        return RESERVED_EXACT.contains(folded) || uiNames.contains(folded)
    }

    @JvmStatic
    fun isTaken(folded: String, pkg: String, names: Map<String, Set<String>>): Boolean {
        val pkgs = names[folded] ?: return false
        for (p in pkgs) if (p != pkg) return true
        return false
    }

    @JvmStatic
    fun parseAccent(s: String?): Int {
        if (s == null || (s.length != 7 && s.length != 9) || s[0] != '#') return 0
        val hex = s.substring(1)
        if (!isHex(hex, false)) return 0
        return -0x1000000 or hex.substring(hex.length - 6).toInt(16)
    }

    @JvmStatic
    fun isText(s: String): Boolean {
        for (i in s.indices) {
            val c = s[i]
            val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == ' ' || TEXT_PUNCTUATION.indexOf(c) >= 0
            if (!ok) return false
        }
        return true
    }

    @JvmStatic
    fun isDigits(s: String): Boolean {
        if (s.isEmpty()) return false
        for (i in s.indices) {
            val c = s[i]
            if (c < '0' || c > '9') return false
        }
        return true
    }

    @JvmStatic
    fun isHex(s: String, lowerOnly: Boolean): Boolean {
        if (s.isEmpty()) return false
        for (i in s.indices) {
            val c = s[i]
            val ok = c in '0'..'9' || c in 'a'..'f' || (!lowerOnly && c in 'A'..'F')
            if (!ok) return false
        }
        return true
    }

    @JvmStatic
    fun isPackageName(s: String?): Boolean {
        if (s.isNullOrEmpty() || s.length > 255) return false
        var segments = 0
        var i = 0
        while (i < s.length) {
            var c = s[i]
            if (c !in 'A'..'Z' && c !in 'a'..'z') return false
            i++
            while (i < s.length && s[i] != '.') {
                c = s[i]
                if (c !in 'A'..'Z' && c !in 'a'..'z' && c !in '0'..'9' && c != '_') return false
                i++
            }
            segments++
            if (i < s.length) i++ else break
            if (i == s.length) return false
        }
        return segments >= 2
    }

    @JvmStatic
    fun isDigest(s: String?): Boolean = s != null && s.length == 64 && isHex(s, true)

    @JvmStatic
    fun isFolded(s: String?): Boolean {
        if (s.isNullOrEmpty() || s.length > Cards.LABEL_MAX) return false
        for (i in s.indices) {
            val c = s[i]
            if (c !in 'a'..'z' && c !in '0'..'9') return false
        }
        return true
    }

    @JvmStatic
    fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef".toCharArray()
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            out[i * 2] = digits[(bytes[i].toInt() ushr 4) and 0xF]
            out[i * 2 + 1] = digits[bytes[i].toInt() and 0xF]
        }
        return String(out)
    }

    @JvmStatic
    fun shortDigest(digest: String): String = grouped(if (digest.length >= 12) digest.substring(0, 12) else digest, 3)

    @JvmStatic
    fun longDigest(digest: String): String = grouped(digest, 8)

    private fun grouped(hex: String, groupsPerLine: Int): String {
        val b = StringBuilder(hex.length + hex.length / 4 + 2)
        var i = 0
        while (i < hex.length) {
            if (i > 0) b.append(if (i / 4 % groupsPerLine == 0) '\n' else ' ')
            b.append(hex, i, minOf(hex.length, i + 4))
            i += 4
        }
        return b.toString()
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    @Throws(Exception::class)
    fun digest(pm: PackageManager, pkg: String): String {
        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pi: PackageInfo = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val info = pi.signingInfo ?: throw Cards.CardException("package has no signing certificate")
            if (info.hasMultipleSigners()) throw Cards.CardException("package has more than one signer")
            info.apkContentsSigners
        } else {
            val pi: PackageInfo = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            pi.signatures
        }
        if (sigs == null || sigs.size != 1) {
            throw Cards.CardException("package has ${sigs?.size ?: 0} signing certificates, expected one")
        }
        return hex(MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray()))
    }

    @JvmStatic
    fun load(file: File): Map<String, Cards.Record> {
        if (!file.exists()) return TreeMap()
        val text = try {
            String(AtomicFile(file).readFully(), StandardCharsets.UTF_8)
        } catch (e: IOException) {
            Log.w(Cards.TAG, "cannot read ${file.name}: $e")
            return TreeMap()
        }
        val records = decode(text)
        if (records == null) {
            Log.w(Cards.TAG, "${file.name} is not valid; moved to ${Cards.STORE_FILE}.bad")
            quarantine(file)
            return TreeMap()
        }
        return records
    }

    @JvmStatic
    fun save(file: File, records: Map<String, Cards.Record>): Boolean {
        val af = AtomicFile(file)
        var out: FileOutputStream? = null
        return try {
            out = af.startWrite()
            out.write(encode(records).toByteArray(StandardCharsets.UTF_8))
            af.finishWrite(out)
            true
        } catch (e: IOException) {
            Log.w(Cards.TAG, "cannot write ${file.name}: $e")
            if (out != null) af.failWrite(out)
            false
        }
    }

    @JvmStatic
    fun decide(file: File, card: Cards.Card, show: Boolean): Boolean {
        val records = load(file).toMutableMap()
        val old = records[card.pkg]
        val now = System.currentTimeMillis()
        val firstSeen = if (old != null && old.digest == card.digest) old.firstSeen else now
        records[card.pkg] = Cards.Record(card.pkg, card.digest, fold(card.label), if (show) Cards.DECISION_SHOW else Cards.DECISION_HIDE, firstSeen, now)
        return save(file, records)
    }

    @JvmStatic
    fun quarantine(file: File): Boolean {
        val bad = File(file.parentFile, "${file.name}.bad")
        if (bad.exists() && !bad.delete()) return false
        return file.renameTo(bad) || file.delete()
    }

    @JvmStatic
    fun decode(text: String?): Map<String, Cards.Record>? {
        if (text == null || text.length > 64 * 1024) return null
        val lines = text.split("\n")
        if (lines.size < 2 || lines[0] != HEADER) return null
        var last = lines.size - 1
        if (lines[last].isEmpty()) last -= 1
        if (last < 1 || lines[last] != FOOTER) return null
        val count = last - 1
        if (count > Cards.MAX_RECORDS) return null
        val out = TreeMap<String, Cards.Record>()
        for (i in 1 until last) {
            var line = lines[i]
            val lastRecord = i == last - 1
            if (!lastRecord) {
                if (!line.endsWith(",")) return null
                line = line.substring(0, line.length - 1)
            }
            val r = decodeRecord(line) ?: return null
            if (out.containsKey(r.pkg)) return null
            out[r.pkg] = r
        }
        return out
    }

    @JvmStatic
    fun decodeRecord(line: String): Cards.Record? {
        val at = intArrayOf(0)
        if (!take(line, at, F_PACKAGE)) return null
        val pkg = until(line, at, '"') ?: return null
        if (!isPackageName(pkg) || !take(line, at, F_DIGEST)) return null
        val digest = until(line, at, '"') ?: return null
        if (!isDigest(digest) || !take(line, at, F_LABEL)) return null
        val label = until(line, at, '"') ?: return null
        if (!isFolded(label) || !take(line, at, F_DECISION)) return null
        val decision = until(line, at, '"') ?: return null
        if (decision != Cards.DECISION_SHOW && decision != Cards.DECISION_HIDE) return null
        if (!take(line, at, F_FIRST_SEEN)) return null
        val firstSeen = until(line, at, ',') ?: return null
        if (firstSeen.length > 18 || !isDigits(firstSeen) || !take(line, at, F_DECIDED_AT)) return null
        val decidedAt = until(line, at, '}') ?: return null
        if (decidedAt.length > 18 || !isDigits(decidedAt) || !take(line, at, "}")) return null
        if (at[0] != line.length) return null
        return Cards.Record(pkg, digest, label, decision, firstSeen.toLong(), decidedAt.toLong())
    }

    private fun take(s: String, at: IntArray, literal: String): Boolean {
        if (!s.startsWith(literal, at[0])) return false
        at[0] += literal.length
        return true
    }

    private fun until(s: String, at: IntArray, stop: Char): String? {
        val end = s.indexOf(stop, at[0])
        if (end < 0) return null
        val out = s.substring(at[0], end)
        at[0] = end
        return out
    }

    @JvmStatic
    fun encode(records: Map<String, Cards.Record>): String {
        var list = ArrayList(records.values)
        if (list.size > Cards.MAX_RECORDS) {
            list.sortWith { a, b -> b.decidedAt.compareTo(a.decidedAt) }
            list = ArrayList(list.subList(0, Cards.MAX_RECORDS))
        }
        list.sortWith { a, b -> a.pkg.compareTo(b.pkg) }
        val b = StringBuilder(HEADER).append('\n')
        for (i in list.indices) {
            val r = list[i]
            if (!isPackageName(r.pkg) || !isDigest(r.digest) || !isFolded(r.label) ||
                (r.decision != Cards.DECISION_SHOW && r.decision != Cards.DECISION_HIDE) ||
                r.firstSeen < 0 || r.decidedAt < 0
            ) {
                throw IllegalArgumentException("record for ${r.pkg} is not valid")
            }
            b.append(F_PACKAGE).append(r.pkg).append(F_DIGEST).append(r.digest).append(F_LABEL).append(r.label)
                .append(F_DECISION).append(r.decision).append(F_FIRST_SEEN).append(r.firstSeen)
                .append(F_DECIDED_AT).append(r.decidedAt).append('}')
            if (i < list.size - 1) b.append(',')
            b.append('\n')
        }
        return b.append(FOOTER).append('\n').toString()
    }

    @JvmStatic
    fun englishUiNames(): Set<String> {
        return setOf(fold("Home"), fold("Settings"), fold("Inputs"), fold("Wi-Fi"), fold("Live TV"), fold("Default home"), fold("Clock"))
    }

    @JvmStatic
    fun selfCheck() {
        if (selfChecked) return
        selfChecked = true
        val bad = ArrayList<String>()
        if (checkLabel("Retro") != null) bad.add("label Retro rejected")
        if (checkLabel("Verified\u202E") == null) bad.add("control character accepted")
        if (checkCaption("NES games") != null) bad.add("caption rejected")
        if (parseAccent("#E8A33D") != -0x175cc3 || parseAccent("#ffe8a33d") != -0x175cc3) bad.add("accent")
        if ("livetv" != fold("Live TV") || !isReserved("hdmi2", englishUiNames())) bad.add("fold or reserved")
        if ("Netflix" != cleanLabel("Netflix\u202E\n", "x")) bad.add("cleanLabel")
        if (!isPackageName("io.example.a") || isPackageName("example")) bad.add("package name")
        val one = TreeMap<String, Cards.Record>()
        val digest = hex(ByteArray(32))
        one["io.example.a"] = Cards.Record("io.example.a", digest, "a", Cards.DECISION_SHOW, 1, 2)
        val back = decode(encode(one))
        if (back == null || back.size != 1 || back["io.example.a"]?.decidedAt != 2L) bad.add("store round trip")
        if (decode("{}") != null) bad.add("bad store accepted")
        Log.w(Cards.TAG, if (bad.isEmpty()) "self-check ok" else "self-check FAILED: $bad")
    }
}
