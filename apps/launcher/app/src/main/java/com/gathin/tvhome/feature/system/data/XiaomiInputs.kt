package com.gathin.tvhome.feature.system.data

import android.content.ComponentName
import android.content.Intent
import java.util.Locale
import java.util.regex.Pattern


object XiaomiInputs {

    private const val PLAYER = "com.xiaomi.mitv.tvplayer"

    @JvmField
    val EXTERNAL_SOURCE = ComponentName(PLAYER, "$PLAYER.ExternalSourceActivity")

    private const val ACTION = "$PLAYER.EXTSRC_PLAY"
    private const val EXTRA_SOURCE = "input"
    private const val FIRST_HDMI = 23
    private const val AV = 2
    private val HDMI = Pattern.compile("HDMI\\s*([1-9])")

    @JvmStatic
    fun source(label: String?): Int {
        if (label == null) return -1
        val upper = label.trim().uppercase(Locale.ROOT)
        val hdmi = HDMI.matcher(upper)
        if (hdmi.find()) {
            val num = hdmi.group(1)?.toIntOrNull() ?: 1
            return FIRST_HDMI + num - 1
        }
        return if (upper.startsWith("AV")) AV else -1
    }

    @JvmStatic
    fun isPlayer(packageName: CharSequence?): Boolean {
        return PLAYER.contentEquals(packageName)
    }

    @JvmStatic
    fun intent(source: Int): Intent {
        return Intent(ACTION).setComponent(EXTERNAL_SOURCE).putExtra(EXTRA_SOURCE, source)
    }
}
