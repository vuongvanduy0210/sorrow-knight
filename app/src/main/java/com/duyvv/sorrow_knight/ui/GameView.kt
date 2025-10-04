package com.duyvv.sorrow_knight.ui

import android.content.Context
import android.content.Intent
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
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withScale
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.duyvv.sorrow_knight.GameOverActivity
import com.duyvv.sorrow_knight.R
import com.duyvv.sorrow_knight.VictoryActivity
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

    // Attack sprite (archer)
    private val attackSprite = BitmapFactory.decodeResource(resources, R.drawable.archer_attack)
    private val attackTotalFrames = 8
    private val attackFrameWidth = attackSprite.width / attackTotalFrames
    private val attackFrameHeight = attackSprite.height
    // Warrior attack sprite (melee)
    private val warriorAttackSprite = BitmapFactory.decodeResource(resources, R.drawable.warrior_attack)
    private val warriorAttackTotalFrames = 4
    private val warriorAttackFrameWidth = warriorAttackSprite.width / warriorAttackTotalFrames
    private val warriorAttackFrameHeight = warriorAttackSprite.height
    // Lancer attack sprite (melee)
    private val lancerAttackSprite = BitmapFactory.decodeResource(resources, R.drawable.lancer_attack)
    private val lancerAttackTotalFrames = 3
    private val lancerAttackFrameWidth = lancerAttackSprite.width / lancerAttackTotalFrames
    private val lancerAttackFrameHeight = lancerAttackSprite.height
    // Guard sprite
    private val guardSprite = BitmapFactory.decodeResource(resources, R.drawable.warrior_guard)
    private val guardTotalFrames = 6
    private val guardFrameWidth = guardSprite.width / guardTotalFrames
    private val guardFrameHeight = guardSprite.height

    // ==================== ANIMATION ====================
    private var currentFrame = 0
    private var frameTimer = 0L
    private val frameDuration = 120L // ms mỗi frame

    // ==================== STATE ====================
    private enum class AttackType { NONE, WARRIOR, ARCHER, LANCER }
    private var currentAttackType = AttackType.NONE
    private var isAttacking = false
    private var hasFiredArrowThisAttack = false
    private var meleeDamageApplied = false
    private var isGuarding = false
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
    private val damageFlashPaint = Paint().apply {
        color = Color.argb(0, 255, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Text paint for HUD
    private val hudTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        typeface = ResourcesCompat.getFont(context, R.font.rebellionsquad_zpprz)
        setShadowLayer(2f, 2f, 2f, Color.BLACK)
    }

    // Temp rects to avoid per-frame allocations
    private val enemySrcRect = Rect()
    private val enemyDstRect = RectF()
    private val playerBoxRect = RectF()
    private val enemyHitboxRect = RectF()
    private var lastDamageFlashAt = 0L
    private val damageFlashDurationMs = 220L

    // Shield effect paint
    private val shieldPaint = Paint().apply {
        color = Color.argb(100, 0, 191, 255) // Màu xanh dương mờ
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

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
    private var damageArcher = 1

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
    // Droppable healing items
    private val mushrooms = mutableListOf<MapItem>()
    private val mushroomBitmap: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.mushroom) }
    private var mushroomHealAmount = 2f
    private var mushroomDropChance = 0.5f // 50% chance for mushroom
    private val mushroomHitboxInsetXRatio = 0.18f
    private val mushroomHitboxInsetYRatio = 0.22f
    private val mushroomDrawWidthPx = 64f
    private val mushroomDrawHeightPx = 64f

    // Droppable shield items (meat)
    private val meats = mutableListOf<MapItem>()
    private val meatBitmap: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.meat) }
    private var meatDropChance = 0.5f // 30% chance for meat
    private val meatHitboxInsetXRatio = 0.18f
    private val meatHitboxInsetYRatio = 0.22f
    private val meatDrawWidthPx = 64f
    private val meatDrawHeightPx = 64f

    // Shield state
    private var hasShield = false

    // ==================== ENEMIES ====================
    private val enemies = mutableListOf<Enemy>()

    // ==================== LEVELS ====================
    private data class LevelConfig(
        val name: String,
        val killTarget: Int,
        val maxEnemies: Int,
        val enemySpeedMultiplier: Float,
        val dropBonus: Float
    )
    private val levels = listOf(
        LevelConfig(
            name = "Level 1",
            killTarget = 1,
            maxEnemies = 3,
            enemySpeedMultiplier = 1.0f,
            dropBonus = 1.0f
        ),
        LevelConfig(
            name = "Level 2",
            killTarget = 1,
            maxEnemies = 3,
            enemySpeedMultiplier = 1.1f,
            dropBonus = 1.1f
        ),
        LevelConfig(
            name = "Level 3",
            killTarget = 1,
            maxEnemies = 3,
            enemySpeedMultiplier = 1.2f,
            dropBonus = 1.2f
        )
    )
    private var currentLevelIndex = 0
    private val currentLevel: LevelConfig
        get() = levels[currentLevelIndex]
    private var killsThisLevel = 0
    private fun effectiveMaxEnemies(): Int = currentLevel.maxEnemies

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

    private val pawnConfig: EnemyAnimConfig by lazy {
        EnemyAnimConfig(
            idleSheet = BitmapFactory.decodeResource(resources, R.drawable.pawn_run), // dùng run làm idle
            moveSheet = BitmapFactory.decodeResource(resources, R.drawable.pawn_run),
            attackSheet = BitmapFactory.decodeResource(resources, R.drawable.pawn_attack),
            idleColumns = 6,
            moveColumns = 6,
            attackColumns = 6,
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
        Enemy.Type.WARRIOR -> pawnConfig
        Enemy.Type.TNT -> tntConfig
    }

    private var lastSpawnTime = 0L
    private val spawnIntervalMs = 100000L
    private val minEnemiesAtStart = 3
    private var previousAliveEnemies = 0

    // Player state (simple health and hit cooldown)
    private var playerHealth = 10f
    private var playerMaxHealth = 10f
    private var lastHitTime = 0L
    private val hitCooldownMs = 800L
    private var damageWarrior = 2
    private var damageLancer = 3
    private var guardDamageMultiplier = 0.4f // nhận 40% sát thương khi đang thủ

    // Game state
    private var isGameOver = false

    // Win condition tracking
    private var enemiesKilled = 0
    private val enemiesToWin = 2 // (unused after levels) giữ để tương thích nhưng không còn dùng để thắng
    private var gameStartTime = 0L
    private var isGameWon = false

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

        // Initialize game start time
        gameStartTime = System.currentTimeMillis()
        enemiesKilled = 0
        isGameWon = false
        currentLevelIndex = 0
        killsThisLevel = 0
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

        // Check for game over condition
        if (playerHealth <= 0f && !isGameOver) {
            isGameOver = true
            showGameOverScreen()
            return
        }

        // Level progression and win condition
        if (!isGameOver && !isGameWon && killsThisLevel >= currentLevel.killTarget) {
            advanceLevel()
            if (isGameWon) {
                showVictoryScreen()
                return
            }
        }
        if (isGameWon && !isGameOver) {
            showVictoryScreen()
            return
        }

        // Don't update game if game is over or won
        if (isGameOver || isGameWon) {
            return
        }

        updateCharacterState()
        updateAnimationFrame()
        updateEnemies()
        updateArrows()
        updateItems()

        drawEnemies(canvas)
        drawArrows(canvas)
        drawMushrooms(canvas)
        drawMeats(canvas)
        drawCharacter(canvas)

        // HUD
        drawPlayerHealthHud(canvas)
        drawKillCountHud(canvas)
        drawGameTimeHud(canvas)
        drawLevelHud(canvas)

        // Damage flash overlay
        drawDamageFlash(canvas)

        postInvalidateOnAnimation()
    }

    // ==================== INIT ====================
    private fun setupCharacterPosition(viewHeight: Int) {
        characterX = 0f
        characterY = (viewHeight - runFrameHeight * scale) / 2f
    }

    // ==================== DRAW ====================
    private fun drawMushrooms(canvas: Canvas) {
        if (mushroomBitmap.width <= 0 || mushroomBitmap.height <= 0) return
        for (m in mushrooms) {
            if (m.isDestroyed) continue
            dstRect.set(m.x, m.y, m.x + mushroomDrawWidthPx, m.y + mushroomDrawHeightPx)
            canvas.drawBitmap(m.bitmap, null, dstRect, paint)
        }
    }

    private fun drawMeats(canvas: Canvas) {
        if (meatBitmap.width <= 0 || meatBitmap.height <= 0) return
        for (m in meats) {
            if (m.isDestroyed) continue
            dstRect.set(m.x, m.y, m.x + meatDrawWidthPx, m.y + meatDrawHeightPx)
            canvas.drawBitmap(m.bitmap, null, dstRect, paint)
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
            val hbWidth = enemyDstRect.width() * 0.3f
            val hbHeight = 8f
            val hbLeft = enemyDstRect.centerX() - hbWidth / 2f
            // Tính vị trí đầu thực tế (bỏ qua padding)
            val actualHeadTop = enemyDstRect.top + (cfg.paddingVertical * cfg.scale)
            val hbTop = actualHeadTop - hbHeight + 30f
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
                when (currentAttackType) {
                    AttackType.ARCHER -> {
                        bitmap = attackSprite
                        frameWidth = attackFrameWidth
                        frameHeight = attackFrameHeight
                        totalFrames = attackTotalFrames
                    }
                    AttackType.WARRIOR -> {
                        bitmap = warriorAttackSprite
                        frameWidth = warriorAttackFrameWidth
                        frameHeight = warriorAttackFrameHeight
                        totalFrames = warriorAttackTotalFrames
                    }
                    AttackType.LANCER -> {
                        bitmap = lancerAttackSprite
                        frameWidth = lancerAttackFrameWidth
                        frameHeight = lancerAttackFrameHeight
                        totalFrames = lancerAttackTotalFrames
                    }
                    else -> {
                        bitmap = attackSprite
                        frameWidth = attackFrameWidth
                        frameHeight = attackFrameHeight
                        totalFrames = attackTotalFrames
                    }
                }
            }
            isGuarding -> {
                bitmap = guardSprite
                frameWidth = guardFrameWidth
                frameHeight = guardFrameHeight
                totalFrames = guardTotalFrames
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
            currentFrame * frameWidth + paddingHorizontal,
            paddingVertical,
            (currentFrame + 1) * frameWidth - paddingHorizontal,
            frameHeight - paddingVertical
        )

        // Vị trí đích
        dstRect.set(
            characterX,
            characterY,
            characterX + frameWidth * scale,
            characterY + frameHeight * scale
        )

        val centerX = dstRect.centerX()
        if (facingLeft) {
            canvas.withScale(-1f, 1f, centerX, dstRect.centerY()) {
                drawBitmap(bitmap, srcRect, dstRect, paint)
            }
        } else {
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        }
//        drawHitbox(canvas)

        // Vẽ hiệu ứng khiên nếu có
        if (hasShield) {
            val shieldRect = RectF(dstRect)
            shieldRect.inset(-8f, -8f) // Mở rộng một chút để bao quanh nhân vật
            canvas.drawOval(shieldRect, shieldPaint)
        }

        // Player health bar moved to HUD (top-left). See drawPlayerHealthHud.
    }

    private fun drawHitbox(canvas: Canvas) {
        canvas.drawRect(dstRect, hitboxPaint)
    }

    private fun drawPlayerHealthHud(canvas: Canvas) {
        val padding = 16f
        val hbWidth = width * 0.3f
        val hbHeight = 16f
        val hbLeft = padding
        val hbTop = padding
        val hbRight = hbLeft + hbWidth
        val hbBottom = hbTop + hbHeight

        canvas.drawRect(hbLeft, hbTop, hbRight, hbBottom, healthBarBgPaint)
        val ratio = if (playerMaxHealth > 0f) playerHealth / playerMaxHealth else 0f
        val fgRight = hbLeft + hbWidth * ratio.coerceIn(0f, 1f)
        canvas.drawRect(hbLeft, hbTop, fgRight, hbBottom, healthBarFgPaint)
        canvas.drawRect(hbLeft, hbTop, hbRight, hbBottom, healthBarBorderPaint)
    }

    private fun drawKillCountHud(canvas: Canvas) {
        val padding = 16f
        val text = "Kills: $enemiesKilled/$enemiesToWin"
        val textX = width - padding
        val textY = padding + hudTextPaint.textSize

        // Draw text aligned to right
        hudTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(text, textX, textY, hudTextPaint)
    }

    private fun drawGameTimeHud(canvas: Canvas) {
        val padding = 16f
        val currentTime = System.currentTimeMillis()
        val elapsedTime = if (gameStartTime > 0) currentTime - gameStartTime else 0L

        val minutes = elapsedTime / 60000
        val seconds = (elapsedTime % 60000) / 1000
        val timeString = String.format("Time: %02d:%02d", minutes, seconds)

        val textX = width - padding
        val textY = padding + hudTextPaint.textSize * 2 + 8f // Vị trí dưới kill count

        // Draw text aligned to right
        hudTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(timeString, textX, textY, hudTextPaint)
    }

    private fun drawLevelHud(canvas: Canvas) {
        val padding = 16f
        val name = currentLevel.name
        val progress = "${killsThisLevel}/${currentLevel.killTarget}"
        val text = "$name  |  $progress"
        hudTextPaint.textAlign = Paint.Align.LEFT
        val x = padding
        val y = padding + hudTextPaint.textSize * 2 + 8f
        canvas.drawText(text, x, y, hudTextPaint)
    }

    private fun drawDamageFlash(canvas: Canvas) {
        if (hasShield) return
        val now = System.currentTimeMillis()
        val elapsed = now - lastDamageFlashAt
        if (elapsed in 0..damageFlashDurationMs) {
            val t = 1f - (elapsed.toFloat() / damageFlashDurationMs.toFloat())
            val alpha = (t * 160).toInt().coerceIn(0, 160) // peak 160 alpha
            damageFlashPaint.color = Color.argb(alpha, 255, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), damageFlashPaint)
        }
    }

    private fun updateItems() {
        if (mushroomBitmap.width <= 0 || mushroomBitmap.height <= 0) return
        if (meatBitmap.width <= 0 || meatBitmap.height <= 0) return
        // Player bounding box
        playerBoxRect.set(
            characterX,
            characterY,
            characterX + runFrameWidth * scale,
            characterY + runFrameHeight * scale
        )
        for (m in mushrooms) {
            if (m.isDestroyed) continue
            // Smaller mushroom hitbox
            val mw = mushroomDrawWidthPx
            val mh = mushroomDrawHeightPx
            val insetX = mw * mushroomHitboxInsetXRatio
            val insetY = mh * mushroomHitboxInsetYRatio
            val mushRect = RectF(
                m.x + insetX,
                m.y + insetY,
                m.x + mw - insetX,
                m.y + mh - insetY
            )
            if (RectF.intersects(playerBoxRect, mushRect)) {
                m.isDestroyed = true
                playerHealth = (playerHealth + mushroomHealAmount).coerceAtMost(playerMaxHealth)
            }
        }
        mushrooms.removeAll { it.isDestroyed }

        for (m in meats) {
            if (m.isDestroyed) continue
            // Smaller meat hitbox
            val mw = meatDrawWidthPx
            val mh = meatDrawHeightPx
            val insetX = mw * meatHitboxInsetXRatio
            val insetY = mh * meatHitboxInsetYRatio
            val meatRect = RectF(
                m.x + insetX,
                m.y + insetY,
                m.x + mw - insetX,
                m.y + mh - insetY
            )
            if (RectF.intersects(playerBoxRect, meatRect)) {
                m.isDestroyed = true
                hasShield = true
            }
        }
        meats.removeAll { it.isDestroyed }
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
        if (isGuarding) {
            val now = System.currentTimeMillis()
            if (now - frameTimer > frameDuration) {
                currentFrame = (currentFrame + 1) % guardTotalFrames
                frameTimer = now
            }
            return
        }

        if (isAttacking || isMoving) {
            val now = System.currentTimeMillis()
            if (now - frameTimer > frameDuration) {
                val total = when {
                    isAttacking && currentAttackType == AttackType.ARCHER -> attackTotalFrames
                    isAttacking && currentAttackType == AttackType.WARRIOR -> warriorAttackTotalFrames
                    isAttacking && currentAttackType == AttackType.LANCER -> lancerAttackTotalFrames
                    else -> runTotalFrames
                }
                currentFrame = (currentFrame + 1) % total
                frameTimer = now

                // Archer: bắn tên ở frame 6
                if (isAttacking && currentAttackType == AttackType.ARCHER) {
                    val attackHitFrameIndex = 6
                    if (!hasFiredArrowThisAttack) {
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
                    if (currentFrame == total - 1) {
                        post { isAttacking = false; currentAttackType = AttackType.NONE }
                    }
                }

                // Melee: gây sát thương ở frame 4
                if (isAttacking && currentAttackType == AttackType.WARRIOR) {
                    val hitFrame = 3
                    if (!meleeDamageApplied && currentFrame == hitFrame) {
                        applyMeleeDamage()
                        meleeDamageApplied = true
                    }
                    if (currentFrame == total - 1) {
                        post { isAttacking = false; currentAttackType = AttackType.NONE; meleeDamageApplied = false }
                    }
                }

                if (isAttacking && currentAttackType == AttackType.LANCER) {
                    val hitFrame = 2
                    if (!meleeDamageApplied && currentFrame == hitFrame) {
                        applyMeleeDamage()
                        meleeDamageApplied = true
                    }
                    if (currentFrame == total - 1) {
                        post { isAttacking = false; currentAttackType = AttackType.NONE; meleeDamageApplied = false }
                    }
                }
            }
        } else {
            currentFrame = 0
        }
    }

    private fun applyMeleeDamage() {
        val width = runFrameWidth * scale
        val height = runFrameHeight * scale
        val meleeWidth = width * 0.6f
        val meleeHeight = height * 0.4f
        val top = characterY + (height - meleeHeight) / 2f
        val left = if (facingLeft) characterX - meleeWidth * 0.2f else characterX + width - meleeWidth * 0.8f
        val rect = RectF(left, top, left + meleeWidth, top + meleeHeight)
        // Apply melee damage depending on attack type
        val killed = ArrayList<Enemy>()
        for (enemy in enemies) {
            if (enemy.isDestroyed) continue
            getEnemyHitboxInto(enemy, enemyHitboxRect)
            if (RectF.intersects(rect, enemyHitboxRect)) {
                val dmg = if (currentAttackType == AttackType.LANCER) damageLancer else damageWarrior
                enemy.health = (enemy.health - dmg).coerceAtLeast(0)
                if (enemy.health == 0) killed.add(enemy)
            }
        }
        if (killed.isNotEmpty()) {
            // Drop items from killed enemies
            killed.forEach { maybeDropItem(it) }
            enemies.removeAll(killed)

            // Increase kill count
            enemiesKilled += killed.size
            killsThisLevel += killed.size

            val canSpawn = (effectiveMaxEnemies() - enemies.size).coerceAtLeast(0)
            val toSpawn = minOf(canSpawn, killed.size)
            repeat(toSpawn) {
                val types = listOf(Enemy.Type.TORCH, Enemy.Type.WARRIOR, Enemy.Type.TNT)
                spawnEnemy(types.random())
            }
        }
    }

    private fun updateEnemies() {
        val now = System.currentTimeMillis()

        // Quản lý số lượng quái: đảm bảo tối thiểu và thay thế ngay khi bị tiêu diệt
        val aliveBefore = enemies.count { !it.isDestroyed }
        if (aliveBefore < minEnemiesAtStart) {
            // Bổ sung để đủ số lượng tối thiểu
            val toSpawn = minEnemiesAtStart - aliveBefore
            repeat(toSpawn) {
                val types = listOf(Enemy.Type.TORCH, Enemy.Type.WARRIOR, Enemy.Type.TNT)
                spawnEnemy(types.random())
            }
        } else if (aliveBefore < previousAliveEnemies && aliveBefore < effectiveMaxEnemies()) {
            // Trường hợp có kẻ địch bị diệt, sẽ respawn trong applyMeleeDamage hoặc checkEnemyHit
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
                val speed = enemy.speedPxPerFrame * currentLevel.enemySpeedMultiplier
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

            // Nếu đang tấn công, phải hoàn thành animation trước khi thay đổi hành động
            if (enemy.state == Enemy.State.ATTACK) {
                // Giữ nguyên trạng thái ATTACK cho đến khi animation kết thúc
                if (shouldAttack) {
                    enemy.facingLeft = playerCenterX < enemyCenterX
                }
                // Không thay đổi state, đợi animation hoàn thành
            } else {
                // Chỉ thay đổi hành động khi không đang tấn công
                if (shouldAttack) {
                    enemy.state = Enemy.State.ATTACK
                    enemy.facingLeft = playerCenterX < enemyCenterX
                } else {
                    enemy.state = Enemy.State.MOVE
                }
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
                        if (enemy.state == Enemy.State.ATTACK) {
                            enemy.attackReady = true
                            // Hoàn thành animation tấn công, chuyển về MOVE
                            enemy.state = Enemy.State.MOVE
                        }
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
                    if (hasShield) {
                        hasShield = false
                        Log.d(TAG, "Shield absorbed the damage!")
                    } else {
                        val incoming = 1f
                        val actual = if (isGuarding) (incoming * guardDamageMultiplier).coerceAtLeast(0f) else incoming
                        Log.d(TAG, "updateEnemies: $actual")
                        playerHealth = (playerHealth - actual).coerceAtLeast(0f)
                    }
                    lastHitTime = now
                    lastDamageFlashAt = now
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

        // Nếu ít hơn tối đa, có thể spawn thêm để đạt maxEnemies
        val aliveAfter = enemies.count { !it.isDestroyed }
        if (aliveAfter < effectiveMaxEnemies()) {
            // Bổ sung để đạt số lượng tối đa của level
            val toSpawn = effectiveMaxEnemies() - aliveAfter
            repeat(toSpawn) {
                val types = listOf(Enemy.Type.TORCH, Enemy.Type.WARRIOR, Enemy.Type.TNT)
                val typeToSpawn = types.random()
                spawnEnemy(typeToSpawn)
            }
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
                    // Archer damage
                    enemy.health = (enemy.health - damageArcher).coerceAtLeast(0)
                    if (enemy.health == 0) {
                        enemy.isDestroyed = true
                        maybeDropItem(enemy)
                        // Increase kill count
                        enemiesKilled++
                        killsThisLevel++
                    }
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

    // Backward-compatible default attack -> Archer
    fun attack() { attackArcher() }

    fun attackWarrior() {
        isAttacking = true
        currentAttackType = AttackType.WARRIOR
        currentFrame = 0
        meleeDamageApplied = false
        postDelayed({ isAttacking = false; currentAttackType = AttackType.NONE }, 500)
    }

    fun attackArcher() {
        isAttacking = true
        currentAttackType = AttackType.ARCHER
        currentFrame = 0
        hasFiredArrowThisAttack = false
        postDelayed({ isAttacking = false; currentAttackType = AttackType.NONE }, 500)
    }

    fun attackLancer() {
        isAttacking = true
        currentAttackType = AttackType.LANCER
        currentFrame = 0
        meleeDamageApplied = false
        postDelayed({ isAttacking = false; currentAttackType = AttackType.NONE }, 500)
    }

    fun startGuarding() {
        isGuarding = true
        currentFrame = 0
    }

    fun stopGuarding() {
        isGuarding = false
    }

    // ==================== GAME OVER ====================
    private fun showGameOverScreen() {
        val intent = Intent(context, GameOverActivity::class.java)
        context.startActivity(intent)
    }

    private fun showVictoryScreen() {
        val gameTime = System.currentTimeMillis() - gameStartTime
        val intent = Intent(context, VictoryActivity::class.java)
        intent.putExtra("game_time", gameTime)
        intent.putExtra("enemies_killed", enemiesKilled)
        intent.putExtra("levels_cleared", currentLevelIndex + if (killsThisLevel >= currentLevel.killTarget) 1 else 0)
        context.startActivity(intent)
    }

    fun resetGame() {
        // Reset player state
        playerHealth = playerMaxHealth
        isGameOver = false
        isGameWon = false
        hasShield = false

        // Reset win condition tracking
        enemiesKilled = 0
        currentLevelIndex = 0
        killsThisLevel = 0
        gameStartTime = System.currentTimeMillis()

        // Reset character position
        setupCharacterPosition(height)

        // Clear and respawn enemies
        enemies.clear()
        spawnEnemy(Enemy.Type.TORCH)
        spawnEnemy(Enemy.Type.WARRIOR)
        spawnEnemy(Enemy.Type.TNT)
        previousAliveEnemies = enemies.count { !it.isDestroyed }

        // Clear arrows and mushrooms
        arrows.clear()
        mushrooms.clear()
        meats.clear()

        // Reset character state
        isMoving = false
        isAttacking = false
        isGuarding = false
        movingDirection = null
        currentAttackType = AttackType.NONE
        currentFrame = 0
        lastHitTime = 0L
        hasFiredArrowThisAttack = false
        meleeDamageApplied = false

        // Restart the game loop
        postInvalidateOnAnimation()
    }

    // ==================== COLLISION ====================

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

    private fun maybeDropItem(enemy: Enemy) {
        val effectiveDropBonus = currentLevel.dropBonus

        // Roll for mushroom
        val mushroomRoll = Math.random().toFloat()
        if (mushroomRoll <= mushroomDropChance * effectiveDropBonus) {
            val dropX = (enemy.x + enemyDstRect.width() * 0.5f).coerceIn(0f,
                (width - mushroomDrawWidthPx).coerceAtLeast(0f)
            )
            val dropY = (enemy.y + enemyDstRect.height() * 0.5f).coerceIn(0f,
                (height - mushroomDrawHeightPx).coerceAtLeast(0f)
            )
            mushrooms.add(
                MapItem(
                    id = "mush_${System.currentTimeMillis()}",
                    x = dropX,
                    y = dropY,
                    bitmap = mushroomBitmap
                )
            )
        }

        // Roll for meat
        val meatRoll = Math.random().toFloat()
        if (meatRoll <= meatDropChance * effectiveDropBonus) {
            val dropX = (enemy.x + enemyDstRect.width() * 0.5f).coerceIn(0f,
                (width - meatDrawWidthPx).coerceAtLeast(0f)
            )
            val dropY = (enemy.y + enemyDstRect.height() * 0.5f).coerceIn(0f,
                (height - meatDrawHeightPx).coerceAtLeast(0f)
            )
            meats.add(
                MapItem(
                    id = "meat_${System.currentTimeMillis()}",
                    x = dropX,
                    y = dropY,
                    bitmap = meatBitmap
                )
            )
        }
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
            speedPxPerFrame = 10f * currentLevel.enemySpeedMultiplier,
            health = 10,
            maxHealth = 10
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

    private fun advanceLevel() {
        // Move to next level or win if last
        if (currentLevelIndex < levels.size - 1) {
            currentLevelIndex += 1
            killsThisLevel = 0
            // Rebalance live enemies to match new maxEnemies
            val aliveEnemies = enemies.count { !it.isDestroyed }
            if (aliveEnemies < effectiveMaxEnemies()) {
                // Spawn additional enemies to reach new maxEnemies
                val toSpawn = effectiveMaxEnemies() - aliveEnemies
                repeat(toSpawn) {
                    val types = listOf(Enemy.Type.TORCH, Enemy.Type.WARRIOR, Enemy.Type.TNT)
                    val typeToSpawn = types.random()
                    spawnEnemy(typeToSpawn)
                }
            } else if (aliveEnemies > effectiveMaxEnemies()) {
                // Remove excess enemies if current count exceeds new max
                while (enemies.count { !it.isDestroyed } > effectiveMaxEnemies()) {
                    val idx = enemies.indexOfFirst { !it.isDestroyed }
                    if (idx >= 0) enemies.removeAt(idx) else break
                }
            }
            previousAliveEnemies = enemies.count { !it.isDestroyed }
        } else {
            isGameWon = true
        }
    }
}