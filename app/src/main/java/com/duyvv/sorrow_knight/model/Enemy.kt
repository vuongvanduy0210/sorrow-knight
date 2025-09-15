package com.duyvv.sorrow_knight.model

import android.graphics.Bitmap
import android.graphics.RectF

data class Enemy(
    val id: String,
    var x: Float,
    var y: Float,
    val bitmap: Bitmap,
    var speedPxPerFrame: Float = 3f,
    var isDestroyed: Boolean = false,
    var attackDamage: Int = 1,
    var canDealDamage: Boolean = true
) {
    fun getBoundingBox(): RectF {
        return RectF(x, y, x + bitmap.width, y + bitmap.height)
    }

    // Patrol parameters
    var patrolLeftBound: Float = 0f
    var patrolRightBound: Float = 0f
    var movingLeft: Boolean = true
    var facingLeft: Boolean = true

    fun update() {
        // Horizontal patrol between left/right bounds
        if (movingLeft) {
            x -= speedPxPerFrame
            if (x <= patrolLeftBound) {
                x = patrolLeftBound
                movingLeft = false
            }
        } else {
            x += speedPxPerFrame
            if (x >= patrolRightBound) {
                x = patrolRightBound
                movingLeft = true
            }
        }
    }

    // Animation/state for sprite-sheet
    enum class State { IDLE, MOVE, ATTACK }
    var state: State = State.MOVE
    var currentFrame: Int = 0
    var frameTimerMs: Long = 0
    var dealtDamageThisAttack: Boolean = false
}


