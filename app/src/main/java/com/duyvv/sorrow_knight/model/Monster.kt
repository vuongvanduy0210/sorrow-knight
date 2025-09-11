package com.duyvv.sorrow_knight.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

class Monster(
    val id: String,
    val spriteSheet: Bitmap,
    val x: Float,
    val y: Float,
    private val cols: Int,      // số frame mỗi hàng
    private val rows: Int,      // số hàng
    private val scale: Float = 2.5f
) {
    enum class State { IDLE, ATTACK }

    private var state = State.IDLE
    private var currentFrame = 0
    private var frameTimer = 0L
    private val frameDuration = 150L // ms mỗi frame

    private val frameWidth = spriteSheet.width / cols
    private val frameHeight = spriteSheet.height / rows

    private val srcRect = Rect()
    private val dstRect = RectF()

    fun setState(newState: State) {
        if (state != newState) {
            state = newState
            currentFrame = 0
        }
    }

    fun update() {
        val now = System.currentTimeMillis()
        if (now - frameTimer > frameDuration) {
            currentFrame = (currentFrame + 1) % cols
            frameTimer = now
        }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val row = when (state) {
            State.IDLE -> 0 // hàng 1 (Idle)
            State.ATTACK -> 2 // hàng 3 (Attack)
        }

        srcRect.set(
            currentFrame * frameWidth,
            row * frameHeight,
            (currentFrame + 1) * frameWidth,
            (row + 1) * frameHeight
        )

        dstRect.set(
            x,
            y,
            x + frameWidth * scale,
            y + frameHeight * scale
        )

        canvas.drawBitmap(spriteSheet, srcRect, dstRect, paint)
    }

    fun getBoundingBox(): RectF {
        return RectF(
            x,
            y,
            x + frameWidth * scale,
            y + frameHeight * scale
        )
    }

    fun isNear(px: Float, py: Float, range: Float): Boolean {
        val dx = (px - (x + frameWidth * scale / 2))
        val dy = (py - (y + frameHeight * scale / 2))
        return (dx * dx + dy * dy) < (range * range)
    }
}
