package com.duyvv.sorrow_knight.model

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Trạng thái runtime của một kẻ địch (enemy).
 * - Vị trí: [x], [y]
 * - Hiển thị: [bitmap], hướng nhìn [facingLeft], hoạt ảnh [state]/[currentFrame]
 * - Chiến đấu: [health]/[maxHealth], [attackDamage], [attackReady], [canDealDamage]
 * - Di chuyển: [speedPxPerFrame], đi tuần cơ bản qua [movingLeft]
 * - AI: [aggroUntilMs] là thời điểm kẻ địch ngừng rượt đuổi nhân vật
 */
data class Enemy(
    val id: String,
    var x: Float,
    var y: Float,
    val bitmap: Bitmap,
    var type: Type = Type.TORCH,
    var speedPxPerFrame: Float = 3f,
    var isDestroyed: Boolean = false,
    var attackDamage: Int = 1,
    var canDealDamage: Boolean = true,
    var health: Int = 3,
    var maxHealth: Int = 3,
    var attackReady: Boolean = false
) {

    /** Hướng đi tuần khi không rượt đuổi/tấn công */
    var movingLeft: Boolean = true
    /** Hướng nhìn hiện tại dùng để vẽ sprite */
    var facingLeft: Boolean = true
    /** Trạng thái hoạt ảnh của sprite-sheet */
    enum class State { IDLE, MOVE, ATTACK }
    enum class Type { TORCH, WARRIOR, TNT }
    var state: State = State.MOVE
    var currentFrame: Int = 0
    var frameTimerMs: Long = 0
    /** Ngăn gây sát thương nhiều lần trong cùng một vòng lặp hoạt ảnh tấn công */
    var dealtDamageThisAttack: Boolean = false

    /**
     * Mốc thời gian (epoch ms) hết hiệu lực rượt đuổi.
     * Khi System.currentTimeMillis() < aggroUntilMs, kẻ địch sẽ rượt theo nhân vật
     * theo luật được cấu hình ở từng level.
     */
    var aggroUntilMs: Long = 0L
}


