package com.gathin.tvhome.core.util

import java.io.IOException
import java.io.Reader

object BoundedInput {
    const val MAX_BODY = 512 * 1024
    const val MAX_LINE = 64 * 1024
    const val MAX_STREAM = 1024 * 1024

    fun text(reader: Reader, limit: Int = MAX_BODY): String {
        val result = StringBuilder()
        val buffer = CharArray(2048)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) return result.toString()
            if (result.length + count > limit) throw IOException("Response too large")
            result.append(buffer, 0, count)
        }
    }

    fun line(reader: Reader, limit: Int = MAX_LINE): String? {
        val result = StringBuilder()
        while (true) {
            val value = reader.read()
            if (value < 0) return result.toString().takeIf { it.isNotEmpty() }
            if (value == 10) return result.toString().removeSuffix("\r")
            if (result.length >= limit) throw IOException("Line too large")
            result.append(value.toChar())
        }
    }
}
