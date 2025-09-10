package com.duyvv.sorrow_knight.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.duyvv.sorrow_knight.R


class GameView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    // Load sprite sheet
    private val spriteSheet = BitmapFactory.decodeResource(resources, R.drawable.warrior_attack)

    // Sprite sheet config
    private val totalFrames = 4
    private val totalRows = 1
    private val frameWidth = spriteSheet.width / totalFrames   // 192
    private val frameHeight = spriteSheet.height / totalRows   // 192

    // Animation state
    private var currentFrame = 0
    private var frameTimer = 0L
    private val frameDuration = 120L // ms per frame

    // Character position
    private var characterX = 0f
    private var characterY = 0f
    private val speed = 8f

    // Scale nhân vật để hiển thị to hơn
    private val scale = 1f

    // Preallocate rect
    private val srcRect = Rect()
    private val dstRect = RectF()
    private val paint = Paint().apply { isFilterBitmap = true }

    enum class Direction {
        UP, DOWN, LEFT, RIGHT
    }

    private var movingDirection: Direction? = null

    /** Đặt vị trí ban đầu sau khi View đo xong kích thước */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Bắt đầu từ giữa bên trái
        characterX = 0f
        characterY = (h - frameHeight * scale) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Update animation frame
        val now = System.currentTimeMillis()
        if (now - frameTimer > frameDuration) {
            currentFrame = (currentFrame + 1) % totalFrames
            frameTimer = now
        }

        // Nếu đang giữ nút -> di chuyển nhân vật
        movingDirection?.let { dir ->
            when (dir) {
                Direction.UP -> characterY -= speed
                Direction.DOWN -> characterY += speed
                Direction.LEFT -> characterX -= speed
                Direction.RIGHT -> characterX += speed
            }

            // Giới hạn không cho đi ra ngoài màn hình
            characterX = characterX.coerceIn(0f, width - frameWidth * scale)
            characterY = characterY.coerceIn(0f, height - frameHeight * scale)
        }

        // Cắt frame từ sprite sheet
        srcRect.set(
            currentFrame * frameWidth,
            0,
            (currentFrame + 1) * frameWidth,
            frameHeight
        )

        // Vùng đích để vẽ
        dstRect.set(
            characterX,
            characterY,
            characterX + frameWidth * scale,
            characterY + frameHeight * scale
        )

        // Vẽ nhân vật
        canvas.drawBitmap(spriteSheet, srcRect, dstRect, paint)

        // Vẽ liên tục
        postInvalidateOnAnimation()
    }

    /** Gọi khi nhấn giữ nút */
    fun startMoving(direction: Direction) {
        movingDirection = direction
    }

    /** Gọi khi nhả nút */
    fun stopMoving() {
        movingDirection = null
    }
}




