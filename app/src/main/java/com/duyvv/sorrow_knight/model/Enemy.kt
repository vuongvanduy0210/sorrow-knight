package com.duyvv.sorrow_knight.model

import android.graphics.Bitmap
import android.graphics.RectF

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

    var movingLeft: Boolean = true
    var facingLeft: Boolean = true
    // Animation/state for sprite-sheet
    enum class State { IDLE, MOVE, ATTACK }
    enum class Type { TORCH, WARRIOR, TNT }
    var state: State = State.MOVE
    var currentFrame: Int = 0
    var frameTimerMs: Long = 0
    var dealtDamageThisAttack: Boolean = false

    // Aggro timer: when now < aggroUntilMs, the enemy will chase the player
    var aggroUntilMs: Long = 0L
}


