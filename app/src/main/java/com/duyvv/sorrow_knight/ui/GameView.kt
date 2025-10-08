package com.duyvv.sorrow_knight.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.withScale
import com.duyvv.sorrow_knight.GameOverActivity
import com.duyvv.sorrow_knight.R
import com.duyvv.sorrow_knight.VictoryActivity
import com.duyvv.sorrow_knight.model.Enemy
import com.duyvv.sorrow_knight.model.MapItem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

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

    // Explosion sprite
    private val explosionSprite = BitmapFactory.decodeResource(resources, R.drawable.explosions)
    private val explosionTotalFrames = 9
    private val explosionFrameWidth = explosionSprite.width / explosionTotalFrames
    private val explosionFrameHeight = explosionSprite.height
    private val explosionScale = 0.5f
    private val explosionFrameDuration = 80L
    private val explosionDamageFrame = 3
    private val explosionDamage = 2f
    private val explosionRadius = 150f

    // ==================== ANIMATION ====================
    private var currentFrame = 0
    private var frameTimer = 0L
    private val frameDuration = 120L

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
    private val baseSpeed = 7f
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

    // Shield effect paints
    private val shieldGlowPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private var shieldPulseTimer = 0L
    private val shieldPulseDuration = 1000L

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

    // ==================== EXPLOSIONS ====================
    private data class Explosion(
        var x: Float,
        var y: Float,
        var currentFrame: Int = 0,
        var frameTimer: Long = System.currentTimeMillis(),
        var hasDealtDamage: Boolean = false,
        var isFinished: Boolean = false
    )
    private val explosions = mutableListOf<Explosion>()
    private val explosionSrcRect = Rect()
    private val explosionDstRect = RectF()

    // ==================== AUDIO ====================
    private var soundPool: SoundPool? = null
    private var swordSoundId = 0
    private var lancerSoundId = 0
    private var hitSoundId = 0
    private var explosionSoundId = 0
    private var healSoundId = 0
    private var meatSoundId = 0
    private var hurtSoundId = 0
    private var wallHitSoundId = 0
    private var isEnabledSound = true

    // ==================== DIRECTION ====================
    enum class Direction { UP, DOWN, LEFT, RIGHT }
    private var movingDirection: Direction? = null

    // ==================== ITEMS ====================
    private val mushrooms = mutableListOf<MapItem>()
    private val mushroomBitmap: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.mushroom) }
    private var mushroomHealAmount = 2f
    private var mushroomDropChance = 1f
    private val mushroomHitboxInsetXRatio = 0.18f
    private val mushroomHitboxInsetYRatio = 0.22f
    private val mushroomDrawWidthPx = 64f
    private val mushroomDrawHeightPx = 64f

    private val meats = mutableListOf<MapItem>()
    private val meatBitmap: Bitmap by lazy { BitmapFactory.decodeResource(resources, R.drawable.meat) }
    private var meatDropChance = 0f
    private val meatHitboxInsetXRatio = 0.18f
    private val meatHitboxInsetYRatio = 0.22f
    private val meatDrawWidthPx = 64f
    private val meatDrawHeightPx = 64f

    private var hasShield = false

    // ==================== ENEMIES ====================
    private val enemies = mutableListOf<Enemy>()

    // ==================== LEVELS ====================
    /**
     * Cấu hình cho từng level: độ khó, số lượng quái tối đa và hệ số tốc độ.
     * Bao gồm cấu hình rượt đuổi (aggro) theo level:
     * - [chaseEnabled]: quái có được phép rượt theo nhân vật không
     * - [chaseDurationMs]: thời gian rượt đuổi sau khi kích hoạt
     * - [chaseRefreshOnReenter]: nếu true, vào lại vùng tấn công sẽ gia hạn thời gian rượt
     */
    private data class LevelConfig(
        val name: String,
        val killTarget: Int,
        val maxEnemies: Int,
        val enemySpeedMultiplier: Float,
        val playerSpeedMultiplier: Float,
        val dropBonus: Float,
        val explosionChance: Float,
        val maxHealthEnemy: Int,
        val chaseEnabled: Boolean,
        val chaseDurationMs: Long,
    )
    private val levels = listOf(
        LevelConfig(
            name = "Level 1",
            killTarget = 2,
            maxEnemies = 4,
            enemySpeedMultiplier = 1.0f,
            playerSpeedMultiplier = 1.0f,
            dropBonus = 1.0f,
            explosionChance = 1f,
            maxHealthEnemy = 10,
            chaseEnabled = false,
            chaseDurationMs = 0L,
        ),
        LevelConfig(
            name = "Level 2",
            killTarget = 3,
            maxEnemies = 5,
            enemySpeedMultiplier = 1.1f,
            playerSpeedMultiplier = 1.3f,
            dropBonus = 1.1f,
            explosionChance = 0.2f,
            maxHealthEnemy = 12,
            chaseEnabled = true,
            chaseDurationMs = 5000L,
        ),
        LevelConfig(
            name = "Level 3",
            killTarget = 5,
            maxEnemies = 7,
            enemySpeedMultiplier = 1.2f,
            playerSpeedMultiplier = 1.5f,
            dropBonus = 1.2f,
            explosionChance = 0.3f,
            maxHealthEnemy = 15,
            chaseEnabled = true,
            chaseDurationMs = 5000L,
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
            idleSheet = BitmapFactory.decodeResource(resources, R.drawable.pawn_run),
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

    private var playerHealth = 10f
    private var playerMaxHealth = 10f
    private var lastHitTime = 0L
    private val hitCooldownMs = 800L
    private var damageWarrior = 2
    private var damageLancer = 3
    private var guardDamageMultiplier = 0.4f

    private var isGameOver = false
    private var enemiesKilled = 0
    private var gameStartTime = 0L
    private var isGameWon = false

    // ==================== UNLOCKS ====================
    interface OnUnlockStateChangeListener {
        fun onUnlockStateChanged(state: UnlockState)
    }

    data class UnlockState(
        val archerUnlocked: Boolean,
        val warriorUnlocked: Boolean,
        val lancerUnlocked: Boolean,
        val guardUnlocked: Boolean
    )

    private var onUnlockStateChangeListener: OnUnlockStateChangeListener? = null

    fun setOnUnlockStateChangeListener(listener: OnUnlockStateChangeListener?) {
        onUnlockStateChangeListener = listener
        // Push current state immediately
        notifyUnlockState()
    }

    // Configure which level each skill is unlocked at (1-based levels)
    private val unlockLevelArcher = 2
    private val unlockLevelWarrior = 1
    private val unlockLevelLancer = 3
    private val unlockLevelGuard = 3

    private fun isArcherUnlocked(): Boolean = currentLevelIndex + 1 >= unlockLevelArcher
    private fun isWarriorUnlocked(): Boolean = currentLevelIndex + 1 >= unlockLevelWarrior
    private fun isLancerUnlocked(): Boolean = currentLevelIndex + 1 >= unlockLevelLancer
    private fun isGuardUnlocked(): Boolean = currentLevelIndex + 1 >= unlockLevelGuard

    private fun notifyUnlockState() {
        onUnlockStateChangeListener?.onUnlockStateChanged(
            UnlockState(
                archerUnlocked = isArcherUnlocked(),
                warriorUnlocked = isWarriorUnlocked(),
                lancerUnlocked = isLancerUnlocked(),
                guardUnlocked = isGuardUnlocked()
            )
        )
    }

    // ==================== LEVEL TRANSITION ====================
    private var isInLevelTransition = false
    private var levelTransitionEndsAtMs = 0L
    private val levelOverlayBgPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val levelOverlayTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
        typeface = ResourcesCompat.getFont(context, R.font.rebellionsquad_zpprz)
        setShadowLayer(2f, 2f, 2f, Color.BLACK)
        textAlign = Paint.Align.CENTER
    }
    private val levelOverlayButtonPaint = Paint().apply {
        color = Color.rgb(30, 144, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val levelOverlayButtonTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        typeface = ResourcesCompat.getFont(context, R.font.rebellionsquad_zpprz)
        setShadowLayer(2f, 2f, 2f, Color.BLACK)
        textAlign = Paint.Align.CENTER
    }
    private val levelTransitionButtonRect = RectF()

    // ==================== Lifecycle ====================
    /**
     * Được gọi khi kích thước view thay đổi. Thiết lập vị trí nhân vật,
     * spawn quái ban đầu, khởi tạo âm thanh và đặt lại các biến trạng thái cấp game.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupCharacterPosition(h)
        enemies.clear()
        spawnEnemy(Enemy.Type.TORCH)
        spawnEnemy(Enemy.Type.WARRIOR)
        spawnEnemy(Enemy.Type.TNT)
        previousAliveEnemies = enemies.count { !it.isDestroyed }
        initAudio()
        gameStartTime = System.currentTimeMillis()
        enemiesKilled = 0
        isGameWon = false
        currentLevelIndex = 0
        killsThisLevel = 0
        try {
            val parent = rootView.findViewById<ScrollingBackgroundView>(R.id.scrollingBackground)
            parent?.setBackgroundByLevel(currentLevelIndex)
        } catch (_: Exception) {}
    }

    /** Khởi tạo hệ thống âm thanh (SoundPool) và nạp các hiệu ứng âm thanh cần dùng. */
    fun initAudio() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        swordSoundId = soundPool?.load(context, R.raw.sword, 1) ?: 0
        lancerSoundId = soundPool?.load(context, R.raw.lancer, 1) ?: 0
        hitSoundId = soundPool?.load(context, R.raw.hit, 1) ?: 0
        explosionSoundId = soundPool?.load(context, R.raw.explosion, 1) ?: 0
        healSoundId = soundPool?.load(context, R.raw.heal, 1) ?: 0
        meatSoundId = soundPool?.load(context, R.raw.meat, 1) ?: 0
        hurtSoundId = soundPool?.load(context, R.raw.hurt, 1) ?: 0
        wallHitSoundId = soundPool?.load(context, R.raw.hit, 1) ?: 0
    }

    /** Hủy tài nguyên âm thanh khi view bị tháo khỏi window để tránh rò rỉ. */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        soundPool?.release()
        soundPool = null
    }

    /**
     * Xử lý chạm màn hình khi đang ở overlay chuyển level:
     * chạm nút Next để chuyển level (nếu đủ điều kiện), hoặc đóng overlay.
     */
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (isInLevelTransition) {
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val x = event.x
                val y = event.y
                if (levelTransitionButtonRect.contains(x, y)) {
                    // If we reached kill target, actually advance level here
                    if (killsThisLevel >= currentLevel.killTarget) {
                        advanceLevel()
                    }
                    // If no more levels or not ready, just close overlay
                    isInLevelTransition = false
                    return true
                }
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Main frame callback. Handles game state flow (game over/win),
     * level transition overlay, and per-frame updates (player, enemies, projectiles, items, effects).
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (playerHealth <= 0f && !isGameOver) {
            isGameOver = true
            showGameOverScreen()
            return
        }

        // Disable auto-advance; instead, show transition overlay when ready
        if (!isGameOver && !isGameWon && killsThisLevel >= currentLevel.killTarget && !isInLevelTransition) {
            isInLevelTransition = true
        }
        if (isGameWon && !isGameOver) {
            showVictoryScreen()
            return
        }

        if (isGameOver) {
            return
        }

        if (!isInLevelTransition) {
            updateCharacterState()
            updateAnimationFrame()
            updateEnemies()
            updateArrows()
            updateItems()
        }
        updateExplosions()

        drawEnemies(canvas)
        drawArrows(canvas)
        drawMushrooms(canvas)
        drawMeats(canvas)
        drawCharacter(canvas)
        drawExplosions(canvas)

        drawPlayerHealthHud(canvas)
        drawGameTimeHud(canvas)
        drawLevelHud(canvas)
        drawDamageFlash(canvas)

        if (isInLevelTransition) {
            drawLevelTransitionOverlay(canvas)
        }

        postInvalidateOnAnimation()
    }

    /** Vẽ overlay chuyển level (tiêu đề, phụ đề, và nút Next). */
    private fun drawLevelTransitionOverlay(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), levelOverlayBgPaint)
        val title = currentLevel.name
        val subtitle = if (killsThisLevel >= currentLevel.killTarget) "Ready to advance" else "Get Ready!"
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawText(title, centerX, centerY - 10f, levelOverlayTextPaint)
        levelOverlayTextPaint.textSize = 28f
        canvas.drawText(subtitle, centerX, centerY + 28f, levelOverlayTextPaint)
        levelOverlayTextPaint.textSize = 48f

        // Draw Next button
        val btnWidth = 200f
        val btnHeight = 64f
        levelTransitionButtonRect.set(
            centerX - btnWidth / 2f,
            centerY + 60f,
            centerX + btnWidth / 2f,
            centerY + 60f + btnHeight
        )
        // Button background (rounded)
        canvas.drawRoundRect(levelTransitionButtonRect, 16f, 16f, levelOverlayButtonPaint)
        // Button text
        canvas.drawText("Next", levelTransitionButtonRect.centerX(), levelTransitionButtonRect.centerY() + 11f, levelOverlayButtonTextPaint)
    }

    // ==================== INIT ====================
    /** Đặt vị trí ban đầu của nhân vật theo chiều dọc giữa màn hình. */
    private fun setupCharacterPosition(viewHeight: Int) {
        characterX = 0f
        characterY = (viewHeight - runFrameHeight * scale) / 2f
    }

    // ==================== DRAW ====================
    /** Vẽ các vật phẩm nấm (mushroom) còn tồn tại trên bản đồ. */
    private fun drawMushrooms(canvas: Canvas) {
        if (mushroomBitmap.width <= 0 || mushroomBitmap.height <= 0) return
        for (m in mushrooms) {
            if (m.isDestroyed) continue
            dstRect.set(m.x, m.y, m.x + mushroomDrawWidthPx, m.y + mushroomDrawHeightPx)
            canvas.drawBitmap(m.bitmap, null, dstRect, paint)
        }
    }

    /** Vẽ các vật phẩm thịt (meat) còn tồn tại trên bản đồ. */
    private fun drawMeats(canvas: Canvas) {
        if (meatBitmap.width <= 0 || meatBitmap.height <= 0) return
        for (m in meats) {
            if (m.isDestroyed) continue
            dstRect.set(m.x, m.y, m.x + meatDrawWidthPx, m.y + meatDrawHeightPx)
            canvas.drawBitmap(m.bitmap, null, dstRect, paint)
        }
    }

    /** Vẽ quái: chọn sheet theo trạng thái, cắt frame, lật theo hướng nhìn và vẽ thanh máu. */
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

            val centerX = enemyDstRect.centerX()
            if (enemy.facingLeft) {
                canvas.withScale(-1f, 1f, centerX, enemyDstRect.centerY()) {
                    drawBitmap(sheet, enemySrcRect, enemyDstRect, paint)
                }
            } else {
                canvas.drawBitmap(sheet, enemySrcRect, enemyDstRect, paint)
            }

            val hbWidth = enemyDstRect.width() * 0.3f
            val hbHeight = 8f
            val hbLeft = enemyDstRect.centerX() - hbWidth / 2f
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

    /** Vẽ các mũi tên (projectile) đang tồn tại. */
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

    /** Vẽ hoạt ảnh nổ theo frame hiện tại. */
    private fun drawExplosions(canvas: Canvas) {
        for (explosion in explosions) {
            if (explosion.isFinished) continue
            explosionSrcRect.set(
                explosion.currentFrame * explosionFrameWidth,
                0,
                (explosion.currentFrame + 1) * explosionFrameWidth,
                explosionFrameHeight
            )
            explosionDstRect.set(
                explosion.x,
                explosion.y,
                explosion.x + explosionFrameWidth * explosionScale,
                explosion.y + explosionFrameHeight * explosionScale
            )
            canvas.drawBitmap(explosionSprite, explosionSrcRect, explosionDstRect, paint)
        }
    }

    /** Vẽ nhân vật: chọn sprite theo trạng thái (tấn công, thủ, chạy, đứng yên) và lật theo hướng nhìn. */
    private fun drawCharacter(canvas: Canvas) {
        val bitmap: Bitmap
        val frameWidth: Int
        val frameHeight: Int

        when {
            isAttacking -> {
                when (currentAttackType) {
                    AttackType.ARCHER -> {
                        bitmap = attackSprite
                        frameWidth = attackFrameWidth
                        frameHeight = attackFrameHeight
                    }
                    AttackType.WARRIOR -> {
                        bitmap = warriorAttackSprite
                        frameWidth = warriorAttackFrameWidth
                        frameHeight = warriorAttackFrameHeight
                    }
                    AttackType.LANCER -> {
                        bitmap = lancerAttackSprite
                        frameWidth = lancerAttackFrameWidth
                        frameHeight = lancerAttackFrameHeight
                    }
                    else -> {
                        bitmap = attackSprite
                        frameWidth = attackFrameWidth
                        frameHeight = attackFrameHeight
                    }
                }
            }
            isGuarding -> {
                bitmap = guardSprite
                frameWidth = guardFrameWidth
                frameHeight = guardFrameHeight
            }
            isMoving -> {
                bitmap = runSprite
                frameWidth = runFrameWidth
                frameHeight = runFrameHeight
            }
            else -> {
                bitmap = runSprite
                frameWidth = runFrameWidth
                frameHeight = runFrameHeight
                currentFrame = 0
            }
        }

        val paddingVertical = 80
        val paddingHorizontal = 60
        srcRect.set(
            currentFrame * frameWidth + paddingHorizontal,
            paddingVertical,
            (currentFrame + 1) * frameWidth - paddingHorizontal,
            frameHeight - paddingVertical
        )

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

        if (hasShield) {
            val now = System.currentTimeMillis()
            val pulseProgress = ((now - shieldPulseTimer) % shieldPulseDuration).toFloat() / shieldPulseDuration
            val pulseFactor = 0.85f + 0.15f * cos(2 * Math.PI * pulseProgress).toFloat()
            val alphaPulse = (100 + 40 * cos(2 * Math.PI * pulseProgress)).toInt().coerceIn(70, 140)

            val shieldRect = RectF(dstRect)
            val inset = -12f * pulseFactor
            shieldRect.inset(inset, inset)

            val centerX = shieldRect.centerX()
            val centerY = shieldRect.centerY()
            val radius = shieldRect.width() / 2f
            shieldGlowPaint.shader = RadialGradient(
                centerX, centerY, radius,
                intArrayOf(
                    Color.argb(alphaPulse, 255, 255, 255),
                    Color.argb((alphaPulse * 0.7f).toInt(), 0, 191, 255),
                    Color.argb((alphaPulse * 0.3f).toInt(), 0, 191, 255),
                    Color.argb(0, 0, 191, 255)
                ),
                floatArrayOf(0f, 0.4f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )

            val shieldPath = Path().apply {
                addOval(shieldRect, Path.Direction.CW)
            }
            canvas.drawPath(shieldPath, shieldGlowPaint)

            val rippleRect1 = RectF(shieldRect)
            val rippleInset1 = inset * 0.6f
            rippleRect1.inset(rippleInset1, rippleInset1)
            val rippleAlpha1 = (80 + 30 * cos(2 * Math.PI * pulseProgress + Math.PI)).toInt().coerceIn(50, 110)
            shieldGlowPaint.shader = RadialGradient(
                centerX, centerY, rippleRect1.width() / 2f,
                intArrayOf(
                    Color.argb(rippleAlpha1, 255, 255, 255),
                    Color.argb((rippleAlpha1 * 0.5f).toInt(), 0, 191, 255),
                    Color.argb(0, 0, 191, 255)
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            val ripplePath1 = Path().apply {
                addOval(rippleRect1, Path.Direction.CW)
            }
            canvas.drawPath(ripplePath1, shieldGlowPaint)

            val rippleRect2 = RectF(shieldRect)
            val rippleInset2 = inset * 0.3f
            rippleRect2.inset(rippleInset2, rippleInset2)
            val fastPulseProgress = ((now - shieldPulseTimer) % (shieldPulseDuration / 2)).toFloat() / (shieldPulseDuration / 2)
            val rippleAlpha2 = (70 + 25 * cos(2 * Math.PI * fastPulseProgress)).toInt().coerceIn(45, 95)
            shieldGlowPaint.shader = RadialGradient(
                centerX, centerY, rippleRect2.width() / 2f,
                intArrayOf(
                    Color.argb(rippleAlpha2, 255, 255, 255),
                    Color.argb((rippleAlpha2 * 0.5f).toInt(), 0, 191, 255),
                    Color.argb(0, 0, 191, 255)
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            val ripplePath2 = Path().apply {
                addOval(rippleRect2, Path.Direction.CW)
            }
            canvas.drawPath(ripplePath2, shieldGlowPaint)
        }
    }

    /** Vẽ thanh máu (HUD) của người chơi ở góc trái trên. */
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

    /** Vẽ thời gian chơi (phút:giây) ở góc phải trên. */
    private fun drawGameTimeHud(canvas: Canvas) {
        val padding = 16f
        val currentTime = System.currentTimeMillis()
        val elapsedTime = if (gameStartTime > 0) currentTime - gameStartTime else 0L

        val minutes = elapsedTime / 60000
        val seconds = (elapsedTime % 60000) / 1000
        val timeString = String.format(Locale.US, "Time: %02d:%02d", minutes, seconds)

        val textX = width - padding
        val textY = padding + hudTextPaint.textSize + 8f
        hudTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(timeString, textX, textY, hudTextPaint)
    }

    /** Vẽ tên level và tiến độ tiêu diệt quái (kills/target). */
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

    /** Vẽ hiệu ứng flash đỏ khi người chơi bị trúng đòn (không có khi đang có khiên). */
    private fun drawDamageFlash(canvas: Canvas) {
        if (hasShield) return
        val now = System.currentTimeMillis()
        val elapsed = now - lastDamageFlashAt
        if (elapsed in 0..damageFlashDurationMs) {
            val t = 1f - (elapsed.toFloat() / damageFlashDurationMs.toFloat())
            val alpha = (t * 160).toInt().coerceIn(0, 160)
            damageFlashPaint.color = Color.argb(alpha, 255, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), damageFlashPaint)
        }
    }

    /** Cập nhật nhặt vật phẩm: kiểm tra va chạm với nấm/thịt và áp dụng hiệu ứng. */
    private fun updateItems() {
        if (mushroomBitmap.width <= 0 || mushroomBitmap.height <= 0) return
        if (meatBitmap.width <= 0 || meatBitmap.height <= 0) return
        playerBoxRect.set(
            characterX,
            characterY,
            characterX + runFrameWidth * scale,
            characterY + runFrameHeight * scale
        )
        for (m in mushrooms) {
            if (m.isDestroyed) continue
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
                playHealSound()
            }
        }
        mushrooms.removeAll { it.isDestroyed }

        for (m in meats) {
            if (m.isDestroyed) continue
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
                shieldPulseTimer = System.currentTimeMillis()
                playMeatSound()
            }
        }
        meats.removeAll { it.isDestroyed }
    }

    /**
     * Cập nhật trạng thái/di chuyển nhân vật theo hướng nhập (nếu có),
     * giới hạn trong màn hình và phát âm thanh khi va vào tường.
     */
    private fun updateCharacterState() {
        isMoving = movingDirection != null

        movingDirection?.let { dir ->
            val speed = baseSpeed * currentLevel.playerSpeedMultiplier

            when (dir) {
                Direction.UP -> characterY -= speed
                Direction.DOWN -> characterY += speed
                Direction.LEFT -> {
                    characterX -= speed
                    facingLeft = true
                }
                Direction.RIGHT -> {
                    characterX += speed
                    facingLeft = false
                }
            }

            // check wall
            val maxX = width - runFrameWidth * scale
            val maxY = height - runFrameHeight * scale
            val newX = characterX.coerceIn(0f, maxX)
            val newY = characterY.coerceIn(0f, maxY)

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

    /**
     * Cập nhật khung hình hoạt ảnh theo trạng thái: thủ, tấn công (3 kiểu), chạy/đứng.
     * Tại khung chỉ định sẽ sinh mũi tên hoặc áp sát gây sát thương.
     */
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

                if (isAttacking && currentAttackType == AttackType.ARCHER) {
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
                        playArcherSound()
                    }
                    if (currentFrame == total - 1) {
                        post { isAttacking = false; currentAttackType = AttackType.NONE }
                    }
                }

                if (isAttacking && currentAttackType == AttackType.WARRIOR) {
                    val hitFrame = 1
                    if (!meleeDamageApplied && currentFrame == hitFrame) {
                        applyMeleeDamage()
                        meleeDamageApplied = true
                        playWarriorSound()
                    }
                    if (currentFrame == total - 1) {
                        post { isAttacking = false; currentAttackType = AttackType.NONE; meleeDamageApplied = false }
                    }
                }

                if (isAttacking && currentAttackType == AttackType.LANCER) {
                    val hitFrame = 1
                    if (!meleeDamageApplied && currentFrame == hitFrame) {
                        applyMeleeDamage()
                        meleeDamageApplied = true
                        playLancerSound()
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

    /** Tính toán vùng chém/đâm cận chiến và gây sát thương lên quái va chạm. */
    private fun applyMeleeDamage() {
        val width = runFrameWidth * scale
        val height = runFrameHeight * scale
        val meleeWidth = width * 0.6f
        val meleeHeight = height * 0.4f
        val top = characterY + (height - meleeHeight) / 2f
        val left = if (facingLeft) characterX - meleeWidth * 0.2f else characterX + width - meleeWidth * 0.8f
        val rect = RectF(left, top, left + meleeWidth, top + meleeHeight)
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
            killed.forEach { maybeDropItem(it) }
            enemies.removeAll(killed)
            enemiesKilled += killed.size
            killsThisLevel += killed.size

            val canSpawn = (effectiveMaxEnemies() - enemies.size).coerceAtLeast(0)
            repeat(canSpawn) {
                val types = listOf(Enemy.Type.TORCH, Enemy.Type.WARRIOR, Enemy.Type.TNT)
                spawnEnemy(types.random())
            }
        }
    }

    /**
     * Update enemies each frame:
     * - Maintain min/max population and compute player hitbox
     * - For each enemy:
     *   - If not ATTACK: decide between patrol vs chase based on level aggro rules
     *   - Chase uses normalized vector to move on X/Y; patrol moves horizontally
     *   - Wrap horizontally; clamp vertically
     *   - Manage ATTACK state transitions and damage windows
     * - Cleanup destroyed enemies and backfill spawns up to cap
     */
    private fun updateEnemies() {
        val now = System.currentTimeMillis()

        val aliveBefore = enemies.count { !it.isDestroyed }
        if (aliveBefore < minEnemiesAtStart) {
            val toSpawn = minEnemiesAtStart - aliveBefore
            repeat(toSpawn) {
                val types = listOf(Enemy.Type.TORCH, Enemy.Type.WARRIOR, Enemy.Type.TNT)
                spawnEnemy(types.random())
            }
        }

        playerBoxRect.set(
            characterX,
            characterY,
            characterX + runFrameWidth * scale,
            characterY + runFrameHeight * scale
        )

        for (enemy in enemies) {
            if (enemy.isDestroyed) continue
            if (enemy.state != Enemy.State.ATTACK) {
                val speed = enemy.speedPxPerFrame * currentLevel.enemySpeedMultiplier

                // Prepare chase/patrol decision
                val cfgMove = getConfig(enemy.type)
                val fwMove = (cfgMove.moveSheet.width / cfgMove.moveColumns)
                val fhMove = cfgMove.moveSheet.height
                val enemyCenterX = enemy.x + (fwMove * cfgMove.scale) / 2f
                val enemyCenterY = enemy.y + (fhMove * cfgMove.scale) / 2f
                val playerCenterX = characterX + (runFrameWidth * scale) / 2f
                val playerCenterY = characterY + (runFrameHeight * scale) / 2f

                // Phát hiện & rượt theo thời gian dựa trên vùng tấn công (có cấu hình theo level)
                val dx = playerCenterX - enemyCenterX
                val dy = playerCenterY - enemyCenterY
                val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val attackRangeXForAggro = fwMove * cfgMove.scale * 0.6f
                val attackRangeYForAggro = fhMove * cfgMove.scale * 0.6f
                val distXForAggro = abs(dx)
                val distYForAggro = abs(dy)
                if (currentLevel.chaseEnabled && distXForAggro < attackRangeXForAggro && distYForAggro < attackRangeYForAggro) {
                    val until = now + currentLevel.chaseDurationMs
                    if (enemy.aggroUntilMs <= now) enemy.aggroUntilMs = until
                }

                if (currentLevel.chaseEnabled && now < enemy.aggroUntilMs) {
                    // Rượt đuổi: di chuyển về phía người chơi theo cả 2 trục
                    if (distance > 0.0001f) {
                        val nx = dx / distance
                        val ny = dy / distance
                        enemy.x += nx * speed
                        enemy.y += ny * speed
                        enemy.facingLeft = nx < 0f
                    }
                    // Giới hạn vị trí theo chiều dọc khi đang rượt
                    val spriteHeight = fhMove * cfgMove.scale
                    if (enemy.y < 0f) enemy.y = 0f
                    if (enemy.y + spriteHeight > height.toFloat()) enemy.y = height.toFloat() - spriteHeight
                } else {
                    // Đi tuần: chỉ di chuyển theo chiều ngang
                    if (enemy.movingLeft) {
                        enemy.x -= speed
                        enemy.facingLeft = true
                    } else {
                        enemy.x += speed
                        enemy.facingLeft = false
                    }
                }

                // Xử lý khi enemy ra khỏi màn hinh
                val spriteWidth = fwMove * cfgMove.scale
                val quickOffset = spriteWidth * 0.25f
                if (enemy.x + spriteWidth < 0f) {
                    enemy.x = width.toFloat() - quickOffset
                }
                if (enemy.x > width.toFloat()) {
                    enemy.x = -spriteWidth + quickOffset
                }
            }

            // check nhân vật có trong tầm tấn công của enemy không
            val cfgForRange = getConfig(enemy.type)
            val fwRange = (cfgForRange.moveSheet.width / cfgForRange.moveColumns)
            val fhRange = cfgForRange.moveSheet.height
            val enemyCenterX = enemy.x + (fwRange * cfgForRange.scale) / 2f
            val enemyCenterY = enemy.y + (fhRange * cfgForRange.scale) / 2f
            val playerCenterX = characterX + (runFrameWidth * scale) / 2f
            val playerCenterY = characterY + (runFrameHeight * scale) / 2f
            val distX = abs(playerCenterX - enemyCenterX)
            val distY = abs(playerCenterY - enemyCenterY)
            val attackRangeX = fwRange * cfgForRange.scale * 0.6f
            val attackRangeY = fhRange * cfgForRange.scale * 0.6f

            val shouldAttack = distX < attackRangeX && distY < attackRangeY

            // set state sang attack nếu trong tầm đánh
            if (enemy.state == Enemy.State.ATTACK) {
                if (shouldAttack) {
                    enemy.facingLeft = playerCenterX < enemyCenterX
                }
            } else {
                if (shouldAttack) {
                    enemy.state = Enemy.State.ATTACK
                    enemy.facingLeft = playerCenterX < enemyCenterX
                } else {
                    enemy.state = Enemy.State.MOVE
                }
            }

            // nếu đang tấn công thì phải hoàn thành anim tấn công rồi di chuyển thì mới được tấn công tiếp
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
                            enemy.state = Enemy.State.MOVE
                        }
                        enemy.dealtDamageThisAttack = false
                    }
                } else {
                    enemy.currentFrame = 0
                }
                enemy.frameTimerMs = now
            }

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
                        lastHitTime = now
                        lastDamageFlashAt = now
                        playHurtSound()
                        Log.d(TAG, "Player bị tấn công! Máu còn: $playerHealth")
                    }
                    if (enemy.state == Enemy.State.ATTACK) {
                        enemy.dealtDamageThisAttack = true
                        enemy.attackReady = false
                    }
                }
            }
        }

        enemies.removeAll { it.isDestroyed }

        val aliveAfter = enemies.count { !it.isDestroyed }
        if (aliveAfter < effectiveMaxEnemies()) {
            val toSpawn = effectiveMaxEnemies() - aliveAfter
            repeat(toSpawn) {
                val types = listOf(Enemy.Type.TORCH, Enemy.Type.WARRIOR, Enemy.Type.TNT)
                val typeToSpawn = types.random()
                spawnEnemy(typeToSpawn)
            }
        }
        previousAliveEnemies = enemies.count { !it.isDestroyed }
    }

    /** Update arrows: movement, culling, and collision damage with enemies. */
    private fun updateArrows() {
        if (arrowBitmap.width <= 0 || arrowBitmap.height <= 0) return
        val iterator = arrows.iterator()
        while (iterator.hasNext()) {
            val arrow = iterator.next()
            val dx = if (arrow.movingLeft) -arrow.speedPxPerFrame else arrow.speedPxPerFrame
            arrow.x += dx

            if (arrow.x + arrowBitmap.width * arrowScale < 0f || arrow.x > width) {
                iterator.remove()
                continue
            }

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
                    enemy.health = (enemy.health - damageArcher).coerceAtLeast(0)
                    if (enemy.health == 0) {
                        enemy.isDestroyed = true
                        maybeDropItem(enemy)
                        enemiesKilled++
                        killsThisLevel++
                    }
                    iterator.remove()
                    break
                }
            }
        }
    }

    /** Advance explosion animations and apply one-time damage at the damage frame. */
    private fun updateExplosions() {
        val now = System.currentTimeMillis()
        val iterator = explosions.iterator()
        while (iterator.hasNext()) {
            val explosion = iterator.next()
            if (explosion.isFinished) {
                iterator.remove()
                continue
            }
            if (now - explosion.frameTimer > explosionFrameDuration) {
                explosion.currentFrame++
                explosion.frameTimer = now
                if (explosion.currentFrame >= explosionTotalFrames) {
                    explosion.isFinished = true
                    continue
                }
            }
            if (explosion.currentFrame == explosionDamageFrame && !explosion.hasDealtDamage) {
                explosion.hasDealtDamage = true
                playExplosionSound()
                val explosionCenterX = explosion.x + (explosionFrameWidth * explosionScale) / 2f
                val explosionCenterY = explosion.y + (explosionFrameHeight * explosionScale) / 2f
                val playerCenterX = characterX + (runFrameWidth * scale) / 2f
                val playerCenterY = characterY + (runFrameHeight * scale) / 2f
                val distX = abs(playerCenterX - explosionCenterX)
                val distY = abs(playerCenterY - explosionCenterY)
                val distance = kotlin.math.sqrt(distX * distX + distY * distY)
                if (distance <= explosionRadius) {
                    if (hasShield) {
                        hasShield = false
                        Log.d(TAG, "Shield absorbed explosion damage!")
                    } else {
                        val actualDamage = if (isGuarding) (explosionDamage * guardDamageMultiplier).coerceAtLeast(0f) else explosionDamage
                        playerHealth = (playerHealth - actualDamage).coerceAtLeast(0f)
                        lastDamageFlashAt = now
                        playHurtSound()
                        Log.d(TAG, "Player hit by explosion! Health left: $playerHealth")
                    }
                }
            }
        }
    }

    /** Bắt đầu di chuyển nhân vật theo hướng [direction]. */
    fun startMoving(direction: Direction) {
        movingDirection = direction
    }

    /** Dừng di chuyển nhân vật. */
    fun stopMoving() {
        movingDirection = null
    }

    /** Gọi hành động tấn công mặc định (bắn cung). */
    fun attack() { attackArcher() }

    /** Nhấn nút Next để chuyển level nếu đủ điều kiện và không đang ở overlay. */
    fun advanceLevelByButton() {
        if (isGameWon) return
        if (isInLevelTransition) return
        advanceLevel()
    }

    /** Tấn công cận chiến kiểu Warrior (nếu đã mở khóa). */
    fun attackWarrior() {
        if (!isWarriorUnlocked()) return
        isAttacking = true
        currentAttackType = AttackType.WARRIOR
        currentFrame = 0
        meleeDamageApplied = false
        postDelayed({ isAttacking = false; currentAttackType = AttackType.NONE }, 500)
    }

    /** Tấn công kiểu Archer (bắn tên), sinh mũi tên tại frame thích hợp. */
    fun attackArcher() {
        if (!isArcherUnlocked()) return
        isAttacking = true
        currentAttackType = AttackType.ARCHER
        currentFrame = 0
        hasFiredArrowThisAttack = false
        postDelayed({ isAttacking = false; currentAttackType = AttackType.NONE }, 500)
    }

    /** Tấn công cận chiến kiểu Lancer (đâm), gây sát thương ở frame chỉ định. */
    fun attackLancer() {
        if (!isLancerUnlocked()) return
        isAttacking = true
        currentAttackType = AttackType.LANCER
        currentFrame = 0
        meleeDamageApplied = false
        postDelayed({ isAttacking = false; currentAttackType = AttackType.NONE }, 500)
    }

    /** Bật trạng thái thủ (giảm sát thương nhận vào). */
    fun startGuarding() {
        if (!isGuardUnlocked()) return
        isGuarding = true
        currentFrame = 0
    }

    /** Tắt trạng thái thủ. */
    fun stopGuarding() {
        isGuarding = false
    }

    /** Mở màn hình Game Over. */
    private fun showGameOverScreen() {
        val intent = Intent(context, GameOverActivity::class.java)
        context.startActivity(intent)
    }

    /** Mở màn hình chiến thắng và truyền thống kê thời gian, số quái, level. */
    private fun showVictoryScreen() {
        val gameTime = System.currentTimeMillis() - gameStartTime
        val intent = Intent(context, VictoryActivity::class.java)
        intent.putExtra("game_time", gameTime)
        intent.putExtra("enemies_killed", enemiesKilled)
        intent.putExtra("levels_cleared", currentLevelIndex + if (killsThisLevel >= currentLevel.killTarget) 1 else 0)
        context.startActivity(intent)
    }

    /** Reset toàn bộ trạng thái game về ban đầu và vẽ lại ngay. */
    fun resetGame() {
        playerHealth = playerMaxHealth
        isGameOver = false
        isGameWon = false
        hasShield = false
        enemiesKilled = 0
        currentLevelIndex = 0
        killsThisLevel = 0
        gameStartTime = System.currentTimeMillis()
        setupCharacterPosition(height)
        enemies.clear()
        spawnEnemy(Enemy.Type.TORCH)
        spawnEnemy(Enemy.Type.WARRIOR)
        spawnEnemy(Enemy.Type.TNT)
        previousAliveEnemies = enemies.count { !it.isDestroyed }
        arrows.clear()
        mushrooms.clear()
        meats.clear()
        explosions.clear()
        isMoving = false
        isAttacking = false
        isGuarding = false
        movingDirection = null
        currentAttackType = AttackType.NONE
        currentFrame = 0
        lastHitTime = 0L
        hasFiredArrowThisAttack = false
        meleeDamageApplied = false
        shieldPulseTimer = System.currentTimeMillis()
        postInvalidateOnAnimation()
        // Notify UI after reset (level and unlocks changed)
        notifyUnlockState()
        // Reset background to level 0
        try {
            val parent = rootView.findViewById<ScrollingBackgroundView>(R.id.scrollingBackground)
            parent?.setBackgroundByLevel(currentLevelIndex)
        } catch (_: Exception) {}
    }

    /** Tính hitbox của enemy dựa trên sheet và scale, ghi vào [outRect]. */
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

    /** Xác suất rơi vật phẩm (nấm/thịt) và hiệu ứng nổ khi quái bị tiêu diệt. */
    private fun maybeDropItem(enemy: Enemy) {
        val effectiveDropBonus = currentLevel.dropBonus
        val pMush = (mushroomDropChance * effectiveDropBonus).coerceIn(0f, 1f)
        val pMeat = (meatDropChance * effectiveDropBonus).coerceIn(0f, 1f)
        val r = Math.random().toFloat()

        if (r < pMush) {
            val dropX = (enemy.x + enemyDstRect.width() * 0.5f).coerceIn(0f, (width - mushroomDrawWidthPx).coerceAtLeast(0f))
            val dropY = (enemy.y + enemyDstRect.height() * 0.5f).coerceIn(0f, (height - mushroomDrawHeightPx).coerceAtLeast(0f))
            mushrooms.add(
                MapItem(
                    id = "mush_${System.currentTimeMillis()}",
                    x = dropX,
                    y = dropY,
                    bitmap = mushroomBitmap
                )
            )
        } else if (r < pMush + pMeat) {
            val dropX = (enemy.x + enemyDstRect.width() * 0.5f).coerceIn(0f, (width - meatDrawWidthPx).coerceAtLeast(0f))
            val dropY = (enemy.y + enemyDstRect.height() * 0.5f).coerceIn(0f, (height - meatDrawHeightPx).coerceAtLeast(0f))
            meats.add(
                MapItem(
                    id = "meat_${System.currentTimeMillis()}",
                    x = dropX,
                    y = dropY,
                    bitmap = meatBitmap
                )
            )
        }

        val explosionRoll = Math.random()
        if (explosionRoll < currentLevel.explosionChance) {
            val explosionX = enemy.x + (enemyDstRect.width() / 2f) - (explosionFrameWidth * explosionScale / 2f)
            val explosionY = enemy.y + (enemyDstRect.height() / 2f) - (explosionFrameHeight * explosionScale / 2f)
            explosions.add(
                Explosion(
                    x = explosionX,
                    y = explosionY
                )
            )
            Log.d(TAG, "Enemy exploded at (${explosionX}, ${explosionY})")
        }
    }

    /** Spawn một enemy [type] tại vị trí hợp lệ ngẫu nhiên và áp dụng hệ số theo level. */
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
            health = currentLevel.maxHealthEnemy,
            maxHealth = currentLevel.maxHealthEnemy
        )
        enemy.movingLeft = listOf(true, false).random()
        enemies.add(enemy)
    }

    private fun playWarriorSound() {
        if (!isEnabledSound) return
        try {
            soundPool?.play(swordSoundId, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing warrior sound", e)
        }
    }

    private fun playLancerSound() {
        if (!isEnabledSound) return
        try {
            soundPool?.play(lancerSoundId, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing lancer sound", e)
        }
    }

    private fun playArcherSound() {
        if (!isEnabledSound) return
        try {
            soundPool?.play(hitSoundId, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing archer sound", e)
        }
    }

    private fun playExplosionSound() {
        if (!isEnabledSound) return
        try {
            soundPool?.play(explosionSoundId, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing explosion sound", e)
        }
    }

    private fun playHealSound() {
        if (!isEnabledSound) return
        try {
            soundPool?.play(healSoundId, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing heal sound", e)
        }
    }

    private fun playMeatSound() {
        if (!isEnabledSound) return
        try {
            soundPool?.play(meatSoundId, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing meat sound", e)
        }
    }

    private fun playHurtSound() {
        if (!isEnabledSound) return
        try {
            soundPool?.play(hurtSoundId, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing hurt sound", e)
        }
    }

    private fun playWallHitSound() {
        if (!isEnabledSound) return
        try {
            soundPool?.play(wallHitSoundId, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing wall hit sound", e)
        }
    }

    /** Bật/tắt âm thanh của trò chơi. */
    fun toggleSound(isEnabled: Boolean) {
        isEnabledSound = isEnabled
    }

    /**
     * Move to the next level, rebalancing enemy count to the new cap,
     * resetting per-level counters/state, updating background, and showing the transition overlay.
     */
    private fun advanceLevel() {
        if (currentLevelIndex < levels.size - 1) {
            currentLevelIndex += 1
            killsThisLevel = 0
            val aliveEnemies = enemies.count { !it.isDestroyed }
            if (aliveEnemies < effectiveMaxEnemies()) {
                val toSpawn = effectiveMaxEnemies() - aliveEnemies
                repeat(toSpawn) {
                    val types = listOf(Enemy.Type.TORCH, Enemy.Type.WARRIOR, Enemy.Type.TNT)
                    val typeToSpawn = types.random()
                    spawnEnemy(typeToSpawn)
                }
            } else if (aliveEnemies > effectiveMaxEnemies()) {
                while (enemies.count { !it.isDestroyed } > effectiveMaxEnemies()) {
                    val idx = enemies.indexOfFirst { !it.isDestroyed }
                    if (idx >= 0) enemies.removeAt(idx) else break
                }
            }
            previousAliveEnemies = enemies.count { !it.isDestroyed }
            // Notify UI about possible unlock changes after level up
            notifyUnlockState()
            // Reset full gameplay state for the new level
            resetStateForNewLevel()
            // Trigger level transition overlay and pause updates until user taps
            isInLevelTransition = true
            levelTransitionEndsAtMs = Long.MAX_VALUE
            // Update background by level
            try {
                val parent = rootView.findViewById<ScrollingBackgroundView>(R.id.scrollingBackground)
                parent?.setBackgroundByLevel(currentLevelIndex)
            } catch (_: Exception) {}
        } else {
            isGameWon = true
        }
    }

    /** Reset transient gameplay state and spawn initial enemies for the new level. */
    private fun resetStateForNewLevel() {
        playerHealth = playerMaxHealth
        hasShield = false
        setupCharacterPosition(height)
        enemies.clear()
        // Spawn initial enemies for the level
        spawnEnemy(Enemy.Type.TORCH)
        spawnEnemy(Enemy.Type.WARRIOR)
        spawnEnemy(Enemy.Type.TNT)
        previousAliveEnemies = enemies.count { !it.isDestroyed }
        arrows.clear()
        mushrooms.clear()
        meats.clear()
        explosions.clear()
        isMoving = false
        isAttacking = false
        isGuarding = false
        movingDirection = null
        currentAttackType = AttackType.NONE
        currentFrame = 0
        lastHitTime = 0L
        hasFiredArrowThisAttack = false
        meleeDamageApplied = false
        shieldPulseTimer = System.currentTimeMillis()
    }
}