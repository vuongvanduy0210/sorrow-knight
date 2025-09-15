package com.duyvv.sorrow_knight

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.duyvv.sorrow_knight.databinding.ActivityGameBinding
import com.duyvv.sorrow_knight.ui.GameView

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupControls()
    }

    private fun setupControls() {
        setupHoldButton(binding.btnUp, GameView.Direction.UP)
        setupHoldButton(binding.btnDown, GameView.Direction.DOWN)
        setupHoldButton(binding.btnLeft, GameView.Direction.LEFT)
        setupHoldButton(binding.btnRight, GameView.Direction.RIGHT)

        binding.btnAttack.setOnClickListener {
            binding.gameView.attack()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHoldButton(button: View, direction: GameView.Direction) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.gameView.startMoving(direction)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.gameView.stopMoving()
                    true
                }
                else -> false
            }
        }
    }
}
