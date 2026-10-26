package com.gathin.tvhome.feature.agent.ui
import com.gathin.tvhome.feature.agent.data.*
import com.gathin.tvhome.feature.agent.coordinator.*
import com.gathin.tvhome.feature.agent.widget.*
import com.gathin.tvhome.feature.agent.adapter.*

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap


object AgentQr {

    @JvmStatic
    @Throws(WriterException::class)
    fun render(text: String, sizePixels: Int): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.MARGIN] = 1
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val side = matrix.width
        val pixels = IntArray(side * side)
        for (row in 0 until side) {
            for (column in 0 until side) {
                val dark = matrix.get(column, row)
                pixels[row * side + column] = if (dark) Color.BLACK else Color.WHITE
            }
        }
        val modules = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(modules, sizePixels, sizePixels, false)
    }
}
