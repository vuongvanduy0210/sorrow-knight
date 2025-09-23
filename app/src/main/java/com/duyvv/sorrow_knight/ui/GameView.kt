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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.duyvv.sorrow_knight.R
import com.duyvv.sorrow_knight.model.Enemy
import com.duyvv.sorrow_knight.model.MapItem
import androidx.core.net.toUri
import androidx.media3.common.Player

class GameView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val TAG = this::class.java.simpleName

    // ==================== SPRITE ====================
    // Run sprite
    private val runSprite = BitmapFactory.decodeResource(resources, R.drawable.archer_run)
    private val runTotalFrames = 6
    private val runFrameWidth = runSprite.width / runTotalFrames
    private val runFrameHeight = runSprite.height

    // Attack sprite
    private val attackSprite = BitmapFactory.decodeResource(resources, R.drawable.archer_attack)
    private val attackTotalFrames = 8
    private val attackFrameWidth = attackSprite.width / attackTotalFrames
    private val attackFrameHeight = attackSprite.height

    // ==================== ANIMATION ====================
    private var currentFrame = 0
    private var frameTimer = 0L
    private val frameDuration = 120L // ms mỗi frame

    // ==================== STATE ====================
    private var isAttacking = false
    private var hasFiredArrowThisAttack = false
    private var isMoving = false
    private var lastWallHitTime = 0L
    private val wallHitCooldownMs = 200L

    // Vị trí nhân vật
    private var characterX = 0f
    private var characterY = 0f
    private val speed = 7f
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

    // Health bar paints
    private val healthBarBgPaint = Paint().apply {
        color = Color.argb(160, 60, 60, 60)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val healthBarFgPaint = Paint().apply {
        color = Color.rgb(220, 20, 60)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val healthBarBorderPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    // Temp rects to avoid per-frame allocations
    private val enemySrcRect = Rect()
    private val enemyDstRect = RectF()
    private val playerBoxRect = RectF()
    private val enemyHitboxRect = RectF()

    // ==================== PROJECTILES ====================
    private data class Arrow(
        var x: Float,
        var y: Float,
        val speedPxPerFrame: Float,
        val movingLeft: Boolean
    )
    private val arrows = mutableListOf<Arrow>()
    private val arrowBitmap: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.arrow) }
    private val arrowScale = 0.5f
    private var arrowSpeedPxPerFrameDefault = 18f

    fun setArrowSpeed(speedPxPerFrame: Float) {
        arrowSpeedPxPerFrameDefault = speedPxPerFrame
    }

    // ==================== AUDIO ====================
    private var attackPlayer: ExoPlayer? = null
    private var hitSoundPlayer: ExoPlayer? = null
    private var isEnabledSound = true

    // ==================== DIRECTION ====================
    enum class Direction { UP, DOWN, LEFT, RIGHT }
    private var movingDirection: Direction? = null

    // ==================== ITEMS ====================
    private val items = mutableListOf<MapItem>()

    // ==================== ENEMIES ====================
    private val enemies = mutableListOf<Enemy>()

    private data class EnemyAnimConfig(
        val idleSheet: Bitmap,
        val moveSheet: Bitmap,
        val attackSheet: Bitmap,
        val idleColumns: Int,
        val moveColumns: Int,
        val attackColumns: Int,
        val frameDurationMs: Long,
        val scale: Float,
        val paddingHorizontal: Int = 0,
        val paddingVertical: Int = 0,
        val hitboxInsetXRatio: Float = 0.25f,
        val hitboxInsetYRatio: Float = 0.30f
    )

    private val torchConfig: EnemyAnimConfig by lazy {
        EnemyAnimConfig(
            idleSheet = BitmapFactory.decodeResource(resources, R.drawable.torch_idle),
            moveSheet = BitmapFactory.decodeResource(resources, R.drawable.torch_move),
            attackSheet = BitmapFactory.decodeResource(resources, R.drawable.torch_attack),
            idleColumns = 6,
            moveColumns = 6,
            attackColumns = 6,
            frameDurationMs = 120L,
            scale = 0.5f
        )
    }

    private val warriorConfig: EnemyAnimConfig by lazy {
        EnemyAnimConfig(
            idleSheet = BitmapFactory.decodeResource(resources, R.drawable.warrior_run), // dùng run làm idle
            moveSheet = BitmapFactory.decodeResource(resources, R.drawable.warrior_run),
            attackSheet = BitmapFactory.decodeResource(resources, R.drawable.warrior_attack),
            idleColumns = 6,
            moveColumns = 6,
            attackColumns = 4,
            frameDurationMs = 110L,
            scale = 0.5f
        )
    }

    private val tntConfig: EnemyAnimConfig by lazy {
        EnemyAnimConfig(
            idleSheet = BitmapFactory.decodeResource(resources, R.drawable.tnt_idle),
            moveSheet = BitmapFactory.decodeResource(resources, R.drawable.tnt_run),
            attackSheet = BitmapFactory.decodeResource(resources, R.drawable.tnt_attack),
            idleColumns = 6,
            moveColumns = 6,
            attackColumns = 7,
            frameDurationMs = 120L,
            scale = 0.5f
        )
    }

    private fun getConfig(type: Enemy.Type): EnemyAnimConfig = when (type) {
        Enemy.Type.TORCH -> torchConfig
        Enemy.Type.WARRIOR -> warriorConfig
        Enemy.Type.TNT -> tntConfig
    }

    private var lastSpawnTime = 0L
    private val spawnIntervalMs = 100000L
    private val maxEnemies = 3
    private val minEnemiesAtStart = 3
    private var previousAliveEnemies = 0

    // Player state (simple health and hit cooldown)
    private var playerHealth = 3
    private var playerMaxHealth = 3
    private var lastHitTime = 0L
    private val hitCooldownMs = 800L

    // ==================== Lifecycle ====================
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupCharacterPosition(h)
        // Spawn đúng 3 quái: Torch, Warrior, TNT
        enemies.clear()
        spawnEnemy(Enemy.Type.TORCH)
        spawnEnemy(Enemy.Type.WARRIOR)
        spawnEnemy(Enemy.Type.TNT)
        previousAliveEnemies = enemies.count { !it.isDestroyed }
        initAudio()
    }

    fun initAudio() {
        attackPlayer = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/${R.raw.pew}".toUri())
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
        }
        hitSoundPlayer = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/${R.raw.hit}".toUri())
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        attackPlayer?.release()
        attackPlayer = null
        hitSoundPlayer?.release()
        hitSoundPlayer = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        updateCharacterState()
        updateAnimationFrame()
        updateEnemies()
        updateArrows()

//        drawItems(canvas)
        drawEnemies(canvas)
        drawArrows(canvas)
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
            val cfg = getConfig(enemy.type)
            val (sheet, columns) = when (enemy.state) {
                Enemy.State.ATTACK -> cfg.attackSheet to cfg.attackColumns
                Enemy.State.MOVE -> cfg.moveSheet to cfg.moveColumns
                else -> cfg.idleSheet to cfg.idleColumns
            }
            val frame = enemy.currentFrame
            val fw = sheet.width / columns
            val fh = sheet.height
            val srcLeft = frame * fw + cfg.paddingHorizontal
            val srcTop = 0 + cfg.paddingVertical
            val srcRight = (frame + 1) * fw - cfg.paddingHorizontal
            val srcBottom = fh - cfg.paddingVertical
            enemySrcRect.set(srcLeft, srcTop, srcRight, srcBottom)

            enemyDstRect.set(
                enemy.x,
                enemy.y,
                enemy.x + fw * cfg.scale,
                enemy.y + fh * cfg.scale
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

            // Draw enemy health bar above sprite
            val hbWidth = enemyDstRect.width() * 0.6f
            val hbHeight = 8f
            val hbLeft = enemyDstRect.centerX() - hbWidth / 2f
            val hbTop = enemyDstRect.top - hbHeight
            val hbRight = hbLeft + hbWidth
            val hbBottom = hbTop + hbHeight
            canvas.drawRect(hbLeft, hbTop, hbRight, hbBottom, healthBarBgPaint)
            val ratio = if (enemy.maxHealth > 0) enemy.health.toFloat() / enemy.maxHealth else 0f
            val fgRight = hbLeft + hbWidth * ratio.coerceIn(0f, 1f)
            canvas.drawRect(hbLeft, hbTop, fgRight, hbBottom, healthBarFgPaint)
            canvas.drawRect(hbLeft, hbTop, hbRight, hbBottom, healthBarBorderPaint)
        }
    }

    private fun drawArrows(canvas: Canvas) {
        if (arrowBitmap.width <= 0 || arrowBitmap.height <= 0) return
        for (arrow in arrows) {
            val left = arrow.x
            val top = arrow.y
            val right = arrow.x + arrowBitmap.width * arrowScale
            val bottom = arrow.y + arrowBitmap.height * arrowScale
            dstRect.set(left, top, right, bottom)

            val centerX = dstRect.centerX()
            if (arrow.movingLeft) {
                canvas.withScale(-1f, 1f, centerX, dstRect.centerY()) {
                    drawBitmap(arrowBitmap, null, dstRect, paint)
                }
            } else {
                canvas.drawBitmap(arrowBitmap, null, dstRect, paint)
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

        // Draw player health bar above player
        val hbWidth = dstRect.width() * 0.6f
        val hbHeight = 10f
        val hbLeft = dstRect.centerX() - hbWidth / 2f
        val hbTop = dstRect.top - hbHeight
        val hbRight = hbLeft + hbWidth
        val hbBottom = hbTop + hbHeight
        canvas.drawRect(hbLeft, hbTop, hbRight, hbBottom, healthBarBgPaint)
        val playerRatio = if (playerMaxHealth > 0) playerHealth.toFloat() / playerMaxHealth else 0f
        val fgRight = hbLeft + hbWidth * playerRatio.coerceIn(0f, 1f)
        canvas.drawRect(hbLeft, hbTop, fgRight, hbBottom, healthBarFgPaint)
        canvas.drawRect(hbLeft, hbTop, hbRight, hbBottom, healthBarBorderPaint)
    }

    private fun drawHitbox(canvas: Canvas) {
        canvas.drawRect(dstRect, hitboxPaint)
    }

    // ==================== UPDATE ====================
    private fun updateCharacterState() {
        isMoving = movingDirection != null

        movingDirection?.let { dir ->
            val oldX = characterX
            val oldY = characterY
            
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

            // Giới hạn trong màn hình và phát âm thanh khi chạm tường
            val maxX = width - runFrameWidth * scale
            val maxY = height - runFrameHeight * scale
            val newX = characterX.coerceIn(0f, maxX)
            val newY = characterY.coerceIn(0f, maxY)
            
            // Kiểm tra xem có chạm tường không
            val hitWall = (newX != characterX) || (newY != characterY)
            if (hitWall) {
                val now = System.currentTimeMillis()
                if (now - lastWallHitTime > wallHitCooldownMs) {
                    playWallHitSound()
                    lastWallHitTime = now
                }
            }
            
            characterX = newX
            characterY = newY
        }
    }

    private fun updateAnimationFrame() {
        if (isAttacking || isMoving) {
            val now = System.currentTimeMillis()
            if (now - frameTimer > frameDuration) {
                val total = if (isAttacking) attackTotalFrames else runTotalFrames
                currentFrame = (currentFrame + 1) % total
                frameTimer = now
                // Bắn mũi tên ở frame 6 (0-based)
                val attackHitFrameIndex = 6
                if (isAttacking && !hasFiredArrowThisAttack) {
                    // Spawn arrow aligned to character vertical center
                    val arrowStartX = if (facingLeft) characterX else characterX + runFrameWidth * scale - (arrowBitmap.width * arrowScale) * 0.25f
                    val playerCenterY = characterY + (runFrameHeight * scale) / 2f
                    val arrowHeight = arrowBitmap.height * arrowScale
                    val arrowStartY = playerCenterY - arrowHeight / 2f
                    arrows.add(
                        Arrow(
                            x = arrowStartX,
                            y = arrowStartY,
                            speedPxPerFrame = arrowSpeedPxPerFrameDefault,
                            movingLeft = facingLeft
                        )
                    )
                    hasFiredArrowThisAttack = true
                    playAttackSound()
                }
            }
        } else {
            currentFrame = 0
        }
    }

    private fun updateEnemies() {
        val now = System.currentTimeMillis()

        // Quản lý số lượng quái: đảm bảo tối thiểu và thay thế ngay khi bị tiêu diệt, tối đa 3
        val aliveBefore = enemies.count { !it.isDestroyed }
        if (aliveBefore < minEnemiesAtStart) {
            // Bổ sung các loại thiếu để đủ 3 loại khác nhau
            val existingTypes = enemies.filter { !it.isDestroyed }.map { it.type }.toMutableSet()
            if (!existingTypes.contains(Enemy.Type.TORCH)) spawnEnemy(Enemy.Type.TORCH)
            if (!existingTypes.contains(Enemy.Type.WARRIOR)) spawnEnemy(Enemy.Type.WARRIOR)
            if (!existingTypes.contains(Enemy.Type.TNT)) spawnEnemy(Enemy.Type.TNT)
        } else if (aliveBefore < previousAliveEnemies && aliveBefore < maxEnemies) {
            // Trường hợp có kẻ địch bị diệt, sẽ respawn cùng loại đó trong checkEnemyHit
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
            // Di chuyển ngang một hướng cố định, wrap khi qua rìa
            if (enemy.state != Enemy.State.ATTACK) {
                val speed = enemy.speedPxPerFrame
                if (enemy.movingLeft) {
                    enemy.x -= speed
                    enemy.facingLeft = true
                } else {
                    enemy.x += speed
                    enemy.facingLeft = false
                }

                val cfgMove = getConfig(enemy.type)
                val fwMove = (cfgMove.moveSheet.width / cfgMove.moveColumns)
                val spriteWidth = fwMove * cfgMove.scale
                val quickOffset = spriteWidth * 0.25f
                // Nếu ra khỏi rìa trái, xuất hiện bên phải (vào nhanh hơn một chút)
                if (enemy.x + spriteWidth < 0f) {
                    enemy.x = width.toFloat() - quickOffset
                }
                // Nếu ra khỏi rìa phải, xuất hiện bên trái (vào nhanh hơn một chút)
                if (enemy.x > width.toFloat()) {
                    enemy.x = -spriteWidth + quickOffset
                }
            }

            // Khoảng cách theo cả X và Y
            val cfgForRange = getConfig(enemy.type)
            val fwRange = (cfgForRange.moveSheet.width / cfgForRange.moveColumns)
            val fhRange = cfgForRange.moveSheet.height
            val enemyCenterX = enemy.x + (fwRange * cfgForRange.scale) / 2f
            val enemyCenterY = enemy.y + (fhRange * cfgForRange.scale) / 2f
            val playerCenterX = characterX + (runFrameWidth * scale) / 2f
            val playerCenterY = characterY + (runFrameHeight * scale) / 2f
            val distX = kotlin.math.abs(playerCenterX - enemyCenterX)
            val distY = kotlin.math.abs(playerCenterY - enemyCenterY)
            val attackRangeX = fwRange * cfgForRange.scale * 0.6f
            val attackRangeY = fhRange * cfgForRange.scale * 0.6f

            // Xác định state
            val shouldAttack = distX < attackRangeX && distY < attackRangeY
            if (shouldAttack) {
                enemy.state = Enemy.State.ATTACK
                enemy.facingLeft = playerCenterX < enemyCenterX
            } else {
                enemy.state = Enemy.State.MOVE
            }

            // Cập nhật frame animation
            val cfgForAnim = getConfig(enemy.type)
            val duration = cfgForAnim.frameDurationMs
            if (now - enemy.frameTimerMs > duration) {
                val looping = enemy.state == Enemy.State.ATTACK || enemy.state == Enemy.State.MOVE
                if (looping) {
                    val columns = when (enemy.state) {
                        Enemy.State.ATTACK -> cfgForAnim.attackColumns
                        Enemy.State.MOVE -> cfgForAnim.moveColumns
                        else -> cfgForAnim.idleColumns
                    }
                    enemy.currentFrame = (enemy.currentFrame + 1) % columns
                    if (enemy.currentFrame == 0) {
                        if (enemy.state == Enemy.State.ATTACK) enemy.attackReady = true
                        enemy.dealtDamageThisAttack = false
                    }
                } else {
                    enemy.currentFrame = 0
                }
                enemy.frameTimerMs = now
            }

            // Enemy attacks on contact
            getEnemyHitboxInto(enemy, enemyHitboxRect)
            if (RectF.intersects(playerBoxRect, enemyHitboxRect)) {
                val canDamage = if (enemy.state == Enemy.State.ATTACK) (enemy.attackReady && !enemy.dealtDamageThisAttack) else now - lastHitTime > hitCooldownMs
                if (canDamage) {
                    playerHealth = (playerHealth - 1).coerceAtLeast(0)
                    lastHitTime = now
                    Log.d(TAG, "Player bị tấn công! Máu còn: $playerHealth")
                    if (enemy.state == Enemy.State.ATTACK) {
                        enemy.dealtDamageThisAttack = true
                        enemy.attackReady = false
                    }
                }
            }
        }

        // Remove destroyed enemies, giữ lại để wrap-around khi ra khỏi màn hình
        enemies.removeAll { it.isDestroyed }

        // Nếu ít hơn tối đa, có thể spawn thêm để đạt tối đa 3 khi muốn
        val aliveAfter = enemies.count { !it.isDestroyed }
        if (aliveAfter < maxEnemies && aliveAfter < minEnemiesAtStart) {
            // Bổ sung những loại còn thiếu
            val existingTypes = enemies.filter { !it.isDestroyed }.map { it.type }.toMutableSet()
            if (!existingTypes.contains(Enemy.Type.TORCH)) spawnEnemy(Enemy.Type.TORCH)
            if (!existingTypes.contains(Enemy.Type.WARRIOR)) spawnEnemy(Enemy.Type.WARRIOR)
            if (!existingTypes.contains(Enemy.Type.TNT)) spawnEnemy(Enemy.Type.TNT)
        }
        previousAliveEnemies = enemies.count { !it.isDestroyed }
    }

    private fun updateArrows() {
        if (arrowBitmap.width <= 0 || arrowBitmap.height <= 0) return
        val iterator = arrows.iterator()
        while (iterator.hasNext()) {
            val arrow = iterator.next()
            val dx = if (arrow.movingLeft) -arrow.speedPxPerFrame else arrow.speedPxPerFrame
            arrow.x += dx

            // Remove when off-screen
            if (arrow.x + arrowBitmap.width * arrowScale < 0f || arrow.x > width) {
                iterator.remove()
                continue
            }

            // Check collision with enemies
            val arrowRect = RectF(
                arrow.x,
                arrow.y,
                arrow.x + arrowBitmap.width * arrowScale,
                arrow.y + arrowBitmap.height * arrowScale
            )
            for (enemy in enemies) {
                if (enemy.isDestroyed) continue
                getEnemyHitboxInto(enemy, enemyHitboxRect)
                if (RectF.intersects(arrowRect, enemyHitboxRect)) {
                    enemy.health = (enemy.health - 1).coerceAtLeast(0)
                    if (enemy.health == 0) enemy.isDestroyed = true
                    iterator.remove()
                    break
                }
            }
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
        Log.d(TAG, "attack: 1111")
        isAttacking = true
        currentFrame = 0
        hasFiredArrowThisAttack = false
        val characterBox = RectF(
            characterX,
            characterY,
            characterX + runFrameWidth * scale,
            characterY + runFrameHeight * scale
        )
//        checkItemCollision(characterBox)
        // Không gây sát thương ngay; damage sẽ áp dụng khi animation kết thúc (playerPendingAttack)

        postDelayed({ isAttacking = false }, 500)
    }

    // ==================== COLLISION ====================
    private fun checkItemCollision(characterBox: RectF) {
        for (item in items) {
            if (!item.isDestroyed && RectF.intersects(characterBox, item.getBoundingBox())) {
                item.isDestroyed = true
                Log.d(TAG, "Item ${item.id} bị phá hủy!")
            }
        }
    }

    private fun checkEnemyHit(characterBox: RectF) {
        val killed = ArrayList<Enemy>()
        for (enemy in enemies) {
            getEnemyHitboxInto(enemy, enemyHitboxRect)
            if (RectF.intersects(characterBox, enemyHitboxRect)) {
                enemy.health = (enemy.health - 1).coerceAtLeast(0)
                if (enemy.health == 0) {
                    killed.add(enemy)
                }
            }
        }
        if (killed.isNotEmpty()) {
            enemies.removeAll(killed)
            // Spawn replacements immediately up to maxEnemies
            val canSpawn = (maxEnemies - enemies.size).coerceAtLeast(0)
            val toSpawn = minOf(canSpawn, killed.size)
            repeat(toSpawn) { index ->
                val typeToRespawn = killed.getOrNull(index)?.type ?: Enemy.Type.TORCH
                spawnEnemy(typeToRespawn)
            }
        }
    }

    private fun getEnemyHitboxInto(enemy: Enemy, outRect: RectF) {
        val cfg = getConfig(enemy.type)
        val fw = (cfg.moveSheet.width / cfg.moveColumns)
        val fh = cfg.moveSheet.height
        val widthScaled = fw * cfg.scale
        val heightScaled = fh * cfg.scale
        val insetX = widthScaled * cfg.hitboxInsetXRatio
        val insetY = heightScaled * cfg.hitboxInsetYRatio
        outRect.set(
            enemy.x + insetX,
            enemy.y + insetY,
            enemy.x + widthScaled - insetX,
            enemy.y + heightScaled - insetY
        )
    }

    private fun spawnEnemy(type: Enemy.Type) {
        val cfg = getConfig(type)
        val frameWidth = cfg.moveSheet.width / cfg.moveColumns
        val frameHeight = cfg.moveSheet.height
        val maxX = (width - frameWidth * cfg.scale).coerceAtLeast(0f)
        val maxY = (height - frameHeight * cfg.scale).coerceAtLeast(0f)
        val spawnX = (0..maxX.toInt()).random().toFloat()
        val spawnY = (0..maxY.toInt()).random().toFloat()
        val enemy = Enemy(
            id = "enemy_${System.currentTimeMillis()}",
            x = spawnX,
            y = spawnY,
            bitmap = cfg.idleSheet,
            type = type,
            speedPxPerFrame = 10f,
            health = 4,
            maxHealth = 4
        )
        enemy.movingLeft = listOf(true, false).random()
        enemies.add(enemy)
    }

    private fun playAttackSound() {
        if (!isEnabledSound) return
        try {
            attackPlayer?.seekTo(0)
            attackPlayer?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing attack sound", e)
        }
    }

    private fun playWallHitSound() {
        if (!isEnabledSound) return
        try {
            hitSoundPlayer?.seekTo(0)
            hitSoundPlayer?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing wall hit sound", e)
        }
    }

    fun toggleSound(isEnabled: Boolean) {
        isEnabledSound = isEnabled
    }
}
