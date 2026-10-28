package com.gathin.tvhome.feature.watching.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

fun InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw IOException("watch response too large")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
