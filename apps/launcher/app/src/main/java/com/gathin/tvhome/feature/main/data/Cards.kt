package com.gathin.tvhome.feature.main.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.util.AtomicFile
import android.util.Log
import com.gathin.tvhome.R
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.TreeMap


object Cards {

    const val CATEGORY = "com.gathin.tvhome.category.DOCK_CARD"
    const val META_DATA = "com.gathin.tvhome.card"
    const val SPEC_SUPPORTED = 1

    @JvmField
    val TAG = "TvHomeCards"

    const val MAX_DECLARED = 32
    const val MAX_SHOWN = 8
    const val MAX_RECORDS = 128
    const val MAX_EVENTS = 64
    const val MAX_DEPTH = 2
    const val MAX_ATTRIBUTES = 16
    const val LABEL_MAX = 24
    const val CAPTION_MAX = 32

    const val STORE_FILE = "cards.json"
    const val DECISION_SHOW = "show"
    const val DECISION_HIDE = "hide"

    enum class State { NEW, RESIGNED, RELABELLED, SHOWN, HIDDEN }

    class Card(
        @JvmField val pkg: String,
        @JvmField val component: ComponentName,
        @JvmField val label: String,
        @JvmField val appLabel: String,
        @JvmField val caption: String?,
        @JvmField val accent: Int,
        @JvmField val digest: String,
        @JvmField val state: State
    ) {
        fun pending(): Boolean = state == State.NEW || state == State.RESIGNED || state == State.RELABELLED
    }

    class Record(
        @JvmField val pkg: String,
        @JvmField val digest: String,
        @JvmField val label: String,
        @JvmField val decision: String,
        @JvmField val firstSeen: Long,
        @JvmField val decidedAt: Long
    )

    class Declared(
        @JvmField val spec: Int,
        @JvmField val label: String,
        @JvmField val caption: String?,
        @JvmField val accent: Int
    )

    class CardException(reason: String) : Exception(reason)

    @JvmStatic
    fun discover(ctx: Context, hidden: Set<String>, records: Map<String, Record>): List<Card> {
        if ((ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) selfCheck()
        val out = ArrayList<Card>()
        val pm = ctx.packageManager
        val declared: List<ResolveInfo> = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(CATEGORY), PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.w(TAG, "card query failed: $e")
            return out
        }
        if (declared.isEmpty()) return out

        val tiles = HashSet<String>()
        val names = HashMap<String, MutableSet<String>>()
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            val ai = ri.activityInfo ?: continue
            tiles.add(ComponentName(ai.packageName, ai.name).flattenToString())
            try {
                addName(names, ri.loadLabel(pm).toString(), ai.packageName)
                addName(names, ai.applicationInfo.loadLabel(pm).toString(), ai.packageName)
            } catch (e: Exception) {
                Log.w(TAG, "${ai.packageName}: label unreadable, $e")
            }
        }
        val uiNames = uiNames(ctx.resources)
        val seen = HashSet<String>()

        var n = 0
        for (ri in declared) {
            if (++n > MAX_DECLARED) {
                Log.w(TAG, "more than $MAX_DECLARED cards declared; the rest are ignored")
                break
            }
            val pkg = ri.activityInfo?.packageName ?: "?"
            try {
                val card = read(ctx, pm, ri, tiles, names, uiNames, hidden, seen, records)
                seen.add(card.pkg)
                out.add(card)
            } catch (e: CardException) {
                Log.w(TAG, "$pkg: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "$pkg: $e")
            }
        }

        val kept = resolveClashes(out, records)
        for (c in out) {
            if (!kept.contains(c)) Log.w(TAG, "${c.pkg}: another card is also named '${c.label}'")
        }
        return kept
    }

    @JvmStatic
    fun resolveClashes(cards: List<Card>, records: Map<String, Record>): List<Card> {
        val groups = HashMap<String, MutableList<Card>>()
        for (c in cards) {
            val key = fold(c.label)
            groups.getOrPut(key) { ArrayList() }.add(c)
        }
        val kept = ArrayList<Card>()
        for (c in cards) {
            val group = groups[fold(c.label)] ?: continue
            if (group.size == 1) {
                kept.add(c)
                continue
            }
            var winner: Card? = null
            for (g in group) {
                if (g.pending()) continue
                if (winner == null || olderDecision(g, winner, records)) winner = g
            }
            if (winner == c) kept.add(c)
        }
        return kept
    }

    private fun olderDecision(a: Card, b: Card, records: Map<String, Record>): Boolean {
        val ra = records[a.pkg]
        val rb = records[b.pkg]
        val fa = ra?.firstSeen ?: Long.MAX_VALUE
        val fb = rb?.firstSeen ?: Long.MAX_VALUE
        if (fa != fb) return fa < fb
        return a.pkg < b.pkg
    }

    private fun read(
        ctx: Context, pm: PackageManager, ri: ResolveInfo, tiles: Set<String>,
        names: Map<String, Set<String>>, uiNames: Set<String>, hidden: Set<String>,
        seen: Set<String>, records: Map<String, Record>
    ): Card {
        val ai = ri.activityInfo ?: throw CardException("not an activity")
        val pkg = ai.packageName
        if (pkg == ctx.packageName) throw CardException("the launcher itself")
        if (hidden.contains(pkg)) throw CardException("listed in hidden_apps")
        if (seen.contains(pkg)) throw CardException("declares a second card; one per package is read")
        if (!ai.exported || !ai.enabled || !ai.applicationInfo.enabled) {
            throw CardException("card activity is not exported and enabled")
        }
        val component = ComponentName(pkg, ai.name)
        if (!tiles.contains(component.flattenToString())) {
            throw CardException("card activity has no LEANBACK_LAUNCHER intent filter")
        }

        val xml = ai.loadXmlMetaData(pm, META_DATA) ?: throw CardException("no <meta-data android:name=\"$META_DATA\" android:resource=...>")
        val d: Declared = try {
            CardParser.parse(xml)
        } finally {
            xml.close()
        }
        if (d.spec != SPEC_SUPPORTED) throw CardException("spec ${d.spec} is not supported (this launcher reads spec $SPEC_SUPPORTED)")

        val digest = CardValidator.digest(pm, pkg)
        val folded = fold(d.label)
        val r = records[pkg]
        val state: State = when {
            r == null -> State.NEW
            r.digest != digest -> State.RESIGNED
            r.label != folded -> State.RELABELLED
            else -> if (DECISION_SHOW == r.decision) State.SHOWN else State.HIDDEN
        }
        val pending = state == State.NEW || state == State.RESIGNED || state == State.RELABELLED

        if (CardValidator.isReserved(folded, uiNames)) throw CardException("label '${d.label}' is reserved")
        if (pending && CardValidator.isTaken(folded, pkg, names)) throw CardException("label '${d.label}' is the name of another installed app")
        val appLabel = CardValidator.cleanLabel(ai.applicationInfo.loadLabel(pm).toString(), pkg)
        if (pending && CardValidator.isTaken(fold(appLabel), pkg, names)) throw CardException("app label '$appLabel' is the name of another installed app")

        return Card(pkg, component, d.label, appLabel, d.caption, d.accent, digest, state)
    }

    private fun addName(names: MutableMap<String, MutableSet<String>>, label: String, pkg: String) {
        val key = fold(label)
        if (key.isEmpty()) return
        names.getOrPut(key) { HashSet() }.add(pkg)
    }

    private fun uiNames(res: Resources): Set<String> {
        val ids = intArrayOf(
            R.string.app_name, R.string.settings, R.string.inputs, R.string.choose_input, R.string.wifi,
            R.string.live_tv, R.string.home_settings, R.string.app_library, R.string.dream_name
        )
        val out = HashSet<String>()
        for (id in ids) out.add(fold(res.getString(id)))
        return out
    }

    @JvmStatic
    fun shown(cards: List<Card>): List<Card> {
        val out = ArrayList<Card>()
        for (c in cards) if (c.state == State.SHOWN) out.add(c)
        out.sortWith(Cards::byLabel)
        return if (out.size > MAX_SHOWN) ArrayList(out.subList(0, MAX_SHOWN)) else out
    }

    @JvmStatic
    fun firstPending(cards: List<Card>): Card? {
        var best: Card? = null
        for (c in cards) {
            if (c.pending() && (best == null || c.pkg < best.pkg)) best = c
        }
        return best
    }

    @JvmStatic
    fun byLabel(a: Card, b: Card): Int {
        val c = a.label.compareTo(b.label, ignoreCase = true)
        return if (c != 0) c else a.pkg.compareTo(b.pkg)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun parse(p: XmlPullParser): Declared = CardParser.parse(p)

    @JvmStatic
    fun checkLabel(label: String?): String? = CardValidator.checkLabel(label)

    @JvmStatic
    fun checkCaption(caption: String?): String? = CardValidator.checkCaption(caption)

    @JvmStatic
    fun cleanLabel(s: String?, fallback: String): String = CardValidator.cleanLabel(s, fallback)

    @JvmStatic
    fun fold(s: String?): String = CardValidator.fold(s)

    @JvmStatic
    fun shortDigest(digest: String): String = CardValidator.shortDigest(digest)

    @JvmStatic
    fun longDigest(digest: String): String = CardValidator.longDigest(digest)

    @JvmStatic
    fun parseAccent(s: String?): Int = CardValidator.parseAccent(s)

    @JvmStatic
    fun isReserved(folded: String, uiNames: Set<String>): Boolean = CardValidator.isReserved(folded, uiNames)

    @JvmStatic
    fun isTaken(folded: String, pkg: String, names: Map<String, Set<String>>): Boolean = CardValidator.isTaken(folded, pkg, names)

    @JvmStatic
    fun isText(s: String): Boolean = CardValidator.isText(s)

    @JvmStatic
    fun isDigits(s: String): Boolean = CardValidator.isDigits(s)

    @JvmStatic
    fun isHex(s: String, lowerOnly: Boolean): Boolean = CardValidator.isHex(s, lowerOnly)

    @JvmStatic
    fun isPackageName(s: String?): Boolean = CardValidator.isPackageName(s)

    @JvmStatic
    fun isDigest(s: String?): Boolean = CardValidator.isDigest(s)

    @JvmStatic
    fun isFolded(s: String?): Boolean = CardValidator.isFolded(s)

    @JvmStatic
    fun hex(bytes: ByteArray): String = CardValidator.hex(bytes)

    @JvmStatic
    fun englishUiNames(): Set<String> = CardValidator.englishUiNames()

    @JvmStatic
    fun storeFile(ctx: Context): File = File(ctx.filesDir, STORE_FILE)

    @JvmStatic
    fun load(file: File): Map<String, Record> = CardValidator.load(file)

    @JvmStatic
    fun save(file: File, records: Map<String, Record>): Boolean = CardValidator.save(file, records)

    @JvmStatic
    fun decide(file: File, card: Card, show: Boolean): Boolean = CardValidator.decide(file, card, show)

    @JvmStatic
    fun quarantine(file: File): Boolean = CardValidator.quarantine(file)

    @JvmStatic
    fun decode(text: String?): Map<String, Record>? = CardValidator.decode(text)

    @JvmStatic
    fun decodeRecord(line: String): Record? = CardValidator.decodeRecord(line)

    @JvmStatic
    fun encode(records: Map<String, Record>): String = CardValidator.encode(records)

    @JvmStatic
    fun selfCheck() = CardValidator.selfCheck()
}
