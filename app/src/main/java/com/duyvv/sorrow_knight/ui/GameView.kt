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
import android.util.Log
import android.view.View
import androidx.core.graphics.withScale
import com.duyvv.sorrow_knight.R
import com.duyvv.sorrow_knight.model.Enemy
import com.duyvv.sorrow_knight.model.MapItem

class GameView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val TAG = this::class.java.simpleName

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
        color = Color.argb(100, 0, 200, 255)
        style = Paint.Style.FILL
    }

    // Hitbox quái để debug
    private val enemyHitboxPaint = Paint().apply {
        color = Color.argb(100, 0, 200, 255)
        style = Paint.Style.FILL
    }

    // Temp rects to avoid per-frame allocations
    private val enemySrcRect = Rect()
    private val enemyDstRect = RectF()
    private val playerBoxRect = RectF()
    private val enemyHitboxRect = RectF()

    // ==================== DIRECTION ====================
    enum class Direction { UP, DOWN, LEFT, RIGHT }
    private var movingDirection: Direction? = null

    // ==================== ITEMS ====================
    private val items = mutableListOf<MapItem>()

    // ==================== ENEMIES ====================
    private val enemies = mutableListOf<Enemy>()
    // Mỗi sheet gồm 6 frame trên 1 hàng
    private val enemyIdleSheet: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.torch_idle) }
    private val enemyMoveSheet: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.torch_move) }
    private val enemyAttackSheet: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.torch_attack) }
    private val enemyColumns = 6
    private val enemyFrameWidth: Int by lazy { enemyIdleSheet.width / enemyColumns }
    private val enemyFrameHeight: Int by lazy { enemyIdleSheet.height }
    private val enemyFrameDuration = 120L
    private val enemyScale = 0.5f
    private val enemyPaddingHorizontal = 0
    private val enemyPaddingVertical = 0
    private var lastSpawnTime = 0L
    private val spawnIntervalMs = 100000L

    // Player state (simple health and hit cooldown)
    private var playerHealth = 3
    private var lastHitTime = 0L
    private val hitCooldownMs = 800L

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
        updateEnemies()

        drawItems(canvas)
        drawEnemies(canvas)
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

    private fun drawEnemies(canvas: Canvas) {
        for (enemy in enemies) {
            if (enemy.isDestroyed) continue
            val sheet = when (enemy.state) {
                Enemy.State.ATTACK -> enemyAttackSheet
                Enemy.State.MOVE -> enemyMoveSheet
                else -> enemyIdleSheet
            }
            val frame = enemy.currentFrame
            val fw = sheet.width / enemyColumns
            val fh = sheet.height
            val srcLeft = frame * fw + enemyPaddingHorizontal
            val srcTop = 0 + enemyPaddingVertical
            val srcRight = (frame + 1) * fw - enemyPaddingHorizontal
            val srcBottom = fh - enemyPaddingVertical
            enemySrcRect.set(srcLeft, srcTop, srcRight, srcBottom)

            enemyDstRect.set(
                enemy.x,
                enemy.y,
                enemy.x + fw * enemyScale,
                enemy.y + fh * enemyScale
            )

            // Vẽ hitbox quái để debug
//            getEnemyHitboxInto(enemy, enemyHitboxRect)
//            canvas.drawRect(enemyHitboxRect, enemyHitboxPaint)

            // Lật ảnh theo hướng nhìn
            val centerX = enemyDstRect.centerX()
            if (enemy.facingLeft) {
                canvas.withScale(-1f, 1f, centerX, enemyDstRect.centerY()) {
                    drawBitmap(sheet, enemySrcRect, enemyDstRect, paint)
                }
            } else {
                canvas.drawBitmap(sheet, enemySrcRect, enemyDstRect, paint)
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
//        drawHitbox(canvas)
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

    private fun updateEnemies() {
        val now = System.currentTimeMillis()

        // Spawn new enemy from the right
        if (now - lastSpawnTime > spawnIntervalMs && enemyIdleSheet.width > 0 && enemyIdleSheet.height > 0) {
            spawnEnemy()
            lastSpawnTime = now
        }

        // Update positions and check collisions
        playerBoxRect.set(
            characterX,
            characterY,
            characterX + runFrameWidth * scale,
            characterY + runFrameHeight * scale
        )

        for (enemy in enemies) {
            if (enemy.isDestroyed) continue
            // Nếu đang tấn công thì không cập nhật vị trí (đứng im)
            if (enemy.state != Enemy.State.ATTACK) {
                val prevMovingLeft = enemy.movingLeft
                enemy.update()
                if (enemy.movingLeft != prevMovingLeft) {
                    enemy.facingLeft = enemy.movingLeft
                }
            }

            // Khoảng cách theo cả X và Y
            val enemyCenterX = enemy.x + (enemyFrameWidth * enemyScale) / 2f
            val enemyCenterY = enemy.y + (enemyFrameHeight * enemyScale) / 2f
            val playerCenterX = characterX + (runFrameWidth * scale) / 2f
            val playerCenterY = characterY + (runFrameHeight * scale) / 2f
            val distX = kotlin.math.abs(playerCenterX - enemyCenterX)
            val distY = kotlin.math.abs(playerCenterY - enemyCenterY)
            val attackRangeX = enemyFrameWidth * enemyScale * 0.6f
            val attackRangeY = enemyFrameHeight * enemyScale * 0.6f

            // Xác định state
            val shouldAttack = distX < attackRangeX && distY < attackRangeY
            if (shouldAttack) {
                enemy.state = Enemy.State.ATTACK
                enemy.facingLeft = playerCenterX < enemyCenterX
            } else {
                enemy.state = Enemy.State.MOVE
            }

            // Cập nhật frame animation
            if (now - enemy.frameTimerMs > enemyFrameDuration) {
                val looping = enemy.state == Enemy.State.ATTACK || enemy.state == Enemy.State.MOVE
                if (looping) {
                    enemy.currentFrame = (enemy.currentFrame + 1) % enemyColumns
                    if (enemy.currentFrame == 0) enemy.dealtDamageThisAttack = false
                } else {
                    enemy.currentFrame = 0
                }
                enemy.frameTimerMs = now
            }

            // Enemy attacks on contact
//            getEnemyHitboxInto(enemy, enemyHitboxRect)
            if (RectF.intersects(playerBoxRect, enemyHitboxRect)) {
                val canDamage = if (enemy.state == Enemy.State.ATTACK) !enemy.dealtDamageThisAttack else now - lastHitTime > hitCooldownMs
                if (canDamage) {
                    playerHealth = (playerHealth - 1).coerceAtLeast(0)
                    lastHitTime = now
                    println("Player bị tấn công! Máu còn: $playerHealth")
                    if (enemy.state == Enemy.State.ATTACK) enemy.dealtDamageThisAttack = true
                }
            }
        }

        // Remove off-screen or destroyed enemies
        enemies.removeAll { it.isDestroyed || it.x + (enemyFrameWidth * enemyScale) < 0 }
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
        checkEnemyHit(characterBox)

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

    private fun checkEnemyHit(characterBox: RectF) {
        for (enemy in enemies) {
//            getEnemyHitboxInto(enemy, enemyHitboxRect)
            if (!enemy.isDestroyed && RectF.intersects(characterBox, enemyHitboxRect)) {
                enemy.isDestroyed = true
                println("Quái vật ${enemy.id} bị tiêu diệt!")
            }
        }
    }

    private fun getEnemyHitboxInto(enemy: Enemy, outRect: RectF) {
        val insetX = (enemyFrameWidth * enemyScale) * 0.25f
        val insetY = (enemyFrameHeight * enemyScale) * 0.30f
        outRect.set(
            enemy.x + insetX,
            enemy.y + insetY,
            enemy.x + enemyFrameWidth * enemyScale - insetX,
            enemy.y + enemyFrameHeight * enemyScale - insetY
        )
    }

    private fun spawnEnemy() {
        val maxX = (width - enemyFrameWidth * enemyScale).coerceAtLeast(0f)
        val maxY = (height - enemyFrameHeight * enemyScale).coerceAtLeast(0f)
        val spawnX = (0..maxX.toInt()).random().toFloat()
        val spawnY = (0..maxY.toInt()).random().toFloat()
        val patrolDistance = (enemyFrameWidth * enemyScale * 3).toInt().coerceAtLeast(30) // ~3 frame widths
        val enemy = Enemy(
            id = "enemy_${System.currentTimeMillis()}",
            x = spawnX,
            y = spawnY,
            bitmap = enemyIdleSheet,
            speedPxPerFrame = 2.5f
        )
        enemy.patrolLeftBound = (spawnX - patrolDistance).coerceAtLeast(0f)
        enemy.patrolRightBound = (spawnX + patrolDistance).coerceAtMost(maxX)
        enemy.movingLeft = listOf(true, false).random()
        enemies.add(enemy)
        // Debug log removed to avoid jank
    }
}
