package com.duyvv.sorrow_knight.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.duyvv.sorrow_knight.R
import androidx.core.graphics.scale

class ScrollingBackgroundView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    private var backgroundBitmap: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.img_bg_1)
    private var scaledBitmap: Bitmap? = null // Bitmap đã được scale

    // Scroll position
    private var scrollOffsetX = 0f

    // Speed
    private val pixelsPerFrame = 3f

    private val srcRect = Rect()
    private val dstRect = Rect()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            scaledBitmap?.recycle() // Giải phóng Bitmap cũ để tránh rò rỉ bộ nhớ
            scaledBitmap = backgroundBitmap.scale(w, h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaledBitmap = scaledBitmap ?: return
        if (scaledBitmap.width <= 0 || scaledBitmap.height <= 0) return

        // Vùng gốc của ảnh
        srcRect.set(0, 0, scaledBitmap.width, scaledBitmap.height)

        // Tính số tile cần để cover chiều ngang
        val tilesNeeded = if (width == 0) 1 else 2

        // Normalize offset để tạo hiệu ứng cuộn liên tục
        val normalizedOffset = ((scrollOffsetX % width) + width) % width

        // Vẽ các tile
        var startX = -normalizedOffset.toInt()
        for (i in 0 until tilesNeeded) {
            val left = startX + i * width
            dstRect.set(left, 0, left + width, height)
            canvas.drawBitmap(scaledBitmap, srcRect, dstRect, paint)
        }

        // Update scroll
        scrollOffsetX += pixelsPerFrame

        // Yêu cầu vẽ frame tiếp theo
        postInvalidateOnAnimation()
    }

    fun setBackgroundByLevel(levelIndex: Int) {
        val drawableRes = when (levelIndex) {
            0 -> R.drawable.img_bg_1
            1 -> R.drawable.img_bg_2
            else -> R.drawable.img_bg_3
        }
        // Recycle previous bitmaps
        scaledBitmap?.recycle()
        backgroundBitmap.recycle()
        // Load new and scale
        backgroundBitmap = BitmapFactory.decodeResource(resources, drawableRes)
        if (width > 0 && height > 0) {
            scaledBitmap = backgroundBitmap.scale(width, height)
        } else {
            scaledBitmap = null
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Giải phóng Bitmap để tránh rò rỉ bộ nhớ
        scaledBitmap?.recycle()
        backgroundBitmap.recycle()
    }
}