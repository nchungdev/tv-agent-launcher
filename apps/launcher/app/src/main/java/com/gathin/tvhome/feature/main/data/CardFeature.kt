package com.gathin.tvhome.feature.main.data

import android.util.Log
import com.gathin.tvhome.feature.main.data.Cards


object CardFeature {

    fun interface Step<T> {
        @Throws(Exception::class)
        fun run(): T
    }

    fun interface Reporter {
        fun failed(t: Throwable)
    }

    @JvmStatic
    private var broken = false

    @JvmField
    var reporter: Reporter = Reporter { t ->
        Log.w(Cards.TAG, "module cards switched off for this session: $t")
    }

    @JvmStatic
    fun working(): Boolean = !broken

    @JvmStatic
    fun <T> run(step: Step<T>, fallback: T): T {
        if (broken) return fallback
        return try {
            step.run()
        } catch (e: Throwable) {
            if (e is Exception || e is LinkageError) {
                broken = true
                try {
                    reporter.failed(e)
                } catch (_: Exception) {
                }
                fallback
            } else {
                throw e
            }
        }
    }

    @JvmStatic
    fun reset() {
        broken = false
    }
}
