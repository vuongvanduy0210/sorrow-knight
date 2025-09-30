package com.duyvv.sorrow_knight.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.duyvv.sorrow_knight.R

class ScrollingBackgroundView(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    private val backgroundBitmap: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.img_bg)

    // Scroll position
    private var scrollOffsetX = 0f

    // Speed
    private val pixelsPerFrame = 3f

    private val srcRect = android.graphics.Rect()
    private val dstRect = android.graphics.Rect()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (backgroundBitmap.width <= 0 || backgroundBitmap.height <= 0) return

        // Vùng gốc của ảnh
        srcRect.set(0, 0, backgroundBitmap.width, backgroundBitmap.height)

        // Vùng đích: full màn hình (fitXY)
        dstRect.set(0, 0, width, height)

        // Tính số tile cần để cover chiều ngang
        val tilesNeeded = if (width == 0) 1 else 2

        // Normalize offset
        val normalizedOffset = ((scrollOffsetX % width) + width) % width

        var startX = -normalizedOffset.toInt()
        for (i in 0 until tilesNeeded) {
            val left = startX + i * width
            dstRect.offsetTo(left, 0)
            canvas.drawBitmap(backgroundBitmap, srcRect, dstRect, paint)
        }

        // Update scroll
        scrollOffsetX += pixelsPerFrame

        // Vẽ frame tiếp theo
        postInvalidateOnAnimation()
    }
}


