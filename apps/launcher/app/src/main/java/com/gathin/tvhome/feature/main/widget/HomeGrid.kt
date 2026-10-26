package com.gathin.tvhome.feature.main.widget

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import java.util.function.IntSupplier


open class HomeGrid @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    private var underlap = 0
    private var underlapSource = IntSupplier { 0 }

    fun underlap(): Int = underlap

    fun roomBelowHeader(): Int = height - underlap

    fun setUnderlapSource(source: IntSupplier) {
        underlapSource = source
    }

    fun headerChanged() {
        if (underlapSource.asInt != underlap) requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        underlap = underlapSource.asInt
        super.onLayout(changed, l, t, r, b)
    }
}
