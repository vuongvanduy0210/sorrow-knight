package com.duyvv.sorrow_knight.model

import android.graphics.Bitmap
import android.graphics.RectF

data class MapItem(
    val id: String,
    var x: Float,
    var y: Float,
    val bitmap: Bitmap,
    var isDestroyed: Boolean = false
) {
    fun getBoundingBox(): RectF {
        return RectF(x, y, x + bitmap.width, y + bitmap.height)
    }
}
