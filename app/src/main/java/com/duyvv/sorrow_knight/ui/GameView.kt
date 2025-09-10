package com.duyvv.sorrow_knight.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withScale
import com.duyvv.sorrow_knight.R
import com.duyvv.sorrow_knight.model.MapItem

class GameView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    // ==================== SPRITE ====================
    // Run sprite
    private val runSprite = BitmapFactory.decodeResource(resources, R.drawable.warrior_run)
    private val runTotalFrames = 6
    private val runFrameWidth = runSprite.width / runTotalFrames
    private val runFrameHeight = runSprite.height

    // Attack sprite
    private val attackSprite = BitmapFactory.decodeResource(resources, R.drawable.warrior_attack)
    private val attackTotalFrames = 4
    private val attackFrameWidth = attackSprite.width / attackTotalFrames
    private val attackFrameHeight = attackSprite.height

    // ==================== ANIMATION ====================
    private var currentFrame = 0
    private var frameTimer = 0L
    private val frameDuration = 120L // ms mỗi frame

    // ==================== STATE ====================
    private var isAttacking = false
    private var isMoving = false

    // Vị trí nhân vật
    private var characterX = 0f
    private var characterY = 0f
    private val speed = 16f
    private val scale = 0.25f

    // Hướng nhân vật (quay mặt)
    private var facingLeft = false

    // ==================== VẼ ====================
    private val srcRect = Rect()
    private val dstRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    // Hitbox màu hồng để debug
    private val hitboxPaint = Paint().apply {
        color = Color.argb(120, 255, 105, 180)
        style = Paint.Style.FILL
    }

    // ==================== DIRECTION ====================
    enum class Direction { UP, DOWN, LEFT, RIGHT }
    private var movingDirection: Direction? = null

    // ==================== ITEMS ====================
    private val items = mutableListOf<MapItem>()

    // ==================== Lifecycle ====================
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupCharacterPosition(h)
        items.clear()
        initItems()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        updateCharacterState()
        updateAnimationFrame()

        drawItems(canvas)
        drawCharacter(canvas)

        postInvalidateOnAnimation()
    }

    // ==================== INIT ====================
    private fun initItems() {
        val chestBitmap = BitmapFactory.decodeResource(resources, R.drawable.chest)
        val chest = MapItem(
            id = "chest_1",
            x = width / 2f - chestBitmap.width / 2,
            y = height / 2f - chestBitmap.height / 2,
            bitmap = chestBitmap
        )
        items.add(chest)
    }

    private fun setupCharacterPosition(viewHeight: Int) {
        characterX = 0f
        characterY = (viewHeight - runFrameHeight * scale) / 2f
    }

    // ==================== DRAW ====================
    private fun drawItems(canvas: Canvas) {
        for (item in items) {
            if (!item.isDestroyed) {
                canvas.drawBitmap(item.bitmap, item.x, item.y, paint)
            }
        }
    }

    private fun drawCharacter(canvas: Canvas) {
        val bitmap: Bitmap
        val frameWidth: Int
        val frameHeight: Int
        val totalFrames: Int

        when {
            isAttacking -> {
                bitmap = attackSprite
                frameWidth = attackFrameWidth
                frameHeight = attackFrameHeight
                totalFrames = attackTotalFrames
            }

            isMoving -> {
                bitmap = runSprite
                frameWidth = runFrameWidth
                frameHeight = runFrameHeight
                totalFrames = runTotalFrames
            }

            else -> {
                bitmap = runSprite
                frameWidth = runFrameWidth
                frameHeight = runFrameHeight
                totalFrames = runTotalFrames
                currentFrame = 0
            }
        }

        // Cắt frame từ sprite sheet
        val paddingVertical = 80
        val paddingHorizontal = 60
        srcRect.set(
            currentFrame * frameWidth + paddingHorizontal, // paddingLeft: khoảng cách giữa các frame
            paddingVertical, // paddingTop: khoảng cách giữa các frame (nếu có)
            (currentFrame + 1) * frameWidth - paddingHorizontal,
            frameHeight - paddingVertical // paddingBottom: khoảng cách dưới cùng
        )

        // Vị trí đích
        dstRect.set(
            characterX,
            characterY,
            characterX + frameWidth * scale,
            characterY + frameHeight * scale
        )

        // Tính điểm giữa để lật ảnh
        val centerX = dstRect.centerX()

        if (facingLeft) {
            // Lật ảnh ngang sang trái
            canvas.withScale(-1f, 1f, centerX, dstRect.centerY()) {
                drawBitmap(bitmap, srcRect, dstRect, paint)
            }
        } else {
            // Bình thường (mặt phải)
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        }
    }

    private fun drawHitbox(canvas: Canvas) {
        canvas.drawRect(dstRect, hitboxPaint)
    }

    // ==================== UPDATE ====================
    private fun updateCharacterState() {
        isMoving = movingDirection != null

        movingDirection?.let { dir ->
            when (dir) {
                Direction.UP -> characterY -= speed
                Direction.DOWN -> characterY += speed
                Direction.LEFT -> {
                    characterX -= speed
                    facingLeft = true // quay mặt trái
                }

                Direction.RIGHT -> {
                    characterX += speed
                    facingLeft = false // quay mặt phải
                }
            }

            // Giới hạn trong màn hình
            val maxX = width - runFrameWidth * scale
            val maxY = height - runFrameHeight * scale
            characterX = characterX.coerceIn(0f, maxX)
            characterY = characterY.coerceIn(0f, maxY)
        }
    }

    private fun updateAnimationFrame() {
        if (isAttacking || isMoving) {
            val now = System.currentTimeMillis()
            if (now - frameTimer > frameDuration) {
                val total = if (isAttacking) attackTotalFrames else runTotalFrames
                currentFrame = (currentFrame + 1) % total
                frameTimer = now
            }
        } else {
            currentFrame = 0
        }
    }

    // ==================== CONTROL ====================
    fun startMoving(direction: Direction) {
        movingDirection = direction
    }

    fun stopMoving() {
        movingDirection = null
    }

    fun attack() {
        isAttacking = true
        currentFrame = 0
        val characterBox = RectF(
            characterX,
            characterY,
            characterX + runFrameWidth * scale,
            characterY + runFrameHeight * scale
        )
        checkItemCollision(characterBox)

        postDelayed({ isAttacking = false }, 500)
    }

    // ==================== COLLISION ====================
    private fun checkItemCollision(characterBox: RectF) {
        for (item in items) {
            if (!item.isDestroyed && RectF.intersects(characterBox, item.getBoundingBox())) {
                item.isDestroyed = true
                println("Item ${item.id} bị phá hủy!")
            }
        }
    }
}
