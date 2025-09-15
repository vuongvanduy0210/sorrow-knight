package com.duyvv.sorrow_knight.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.duyvv.sorrow_knight.R
import androidx.core.graphics.withTranslation

class ScrollingBackgroundView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    private val backgroundBitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.img_bg)

    // Scroll position in pixels (x axis)
    private var scrollOffsetX = 0f

    // Pixels per frame. Adjust for speed (device-independent by tying to frame time if needed)
    private val pixelsPerFrame = 3f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (backgroundBitmap.width <= 0 || backgroundBitmap.height <= 0) {
            return
        }

        // Scale background to match view height, keep aspect ratio, then tile horizontally
        val scale = height.toFloat() / backgroundBitmap.height.toFloat()
        val scaledWidth = (backgroundBitmap.width * scale).toInt()
        val scaledHeight = height

        // How many tiles needed to cover the width plus one extra for scrolling gap
        val tilesNeeded = if (scaledWidth == 0) 1 else (width / scaledWidth) + 2

        // Normalize offset to [0, scaledWidth)
        val normalizedOffset = ((scrollOffsetX % scaledWidth) + scaledWidth) % scaledWidth

        var startX = -normalizedOffset
        for (i in 0 until tilesNeeded) {
            val left = startX + i * scaledWidth
            canvas.withTranslation(left, 0f) {
                scale(scale, scale)
                drawBitmap(backgroundBitmap, 0f, 0f, paint)
            }
        }

        // Advance scroll
        scrollOffsetX += pixelsPerFrame

        // Schedule next frame
        postInvalidateOnAnimation()
    }
}


