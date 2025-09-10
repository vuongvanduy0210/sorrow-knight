package com.duyvv.sorrow_knight.model

import android.graphics.Bitmap
import android.graphics.RectF

data class Wall(
    val rect: RectF,       // Vị trí và kích thước tường
    val bitmap: Bitmap     // Ảnh tường
)
