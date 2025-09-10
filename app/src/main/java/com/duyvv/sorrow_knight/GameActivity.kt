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

        // Khoi tao ViewBinding
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupHoldButton(binding.btnUp, GameView.Direction.UP)
        setupHoldButton(binding.btnDown, GameView.Direction.DOWN)
        setupHoldButton(binding.btnLeft, GameView.Direction.LEFT)
        setupHoldButton(binding.btnRight, GameView.Direction.RIGHT)

        // Xu ly su kien click cac nut
        /*binding.btnUp.setOnClickListener {
            binding.gameView.move(GameView.Direction.UP)
        }

        binding.btnDown.setOnClickListener {
            binding.gameView.move(GameView.Direction.DOWN)
        }

        binding.btnLeft.setOnClickListener {
            binding.gameView.move(GameView.Direction.LEFT)
        }

        binding.btnRight.setOnClickListener {
            binding.gameView.move(GameView.Direction.RIGHT)
        }

        binding.btnUp.setOnLongClickListener {
            binding.gameView.move(GameView.Direction.UP)
            true
        }*/
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

