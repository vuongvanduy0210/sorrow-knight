package com.duyvv.sorrow_knight

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.duyvv.sorrow_knight.base.BaseActivity
import com.duyvv.sorrow_knight.databinding.ActivityGameBinding
import com.duyvv.sorrow_knight.ui.GameView

class GameActivity : BaseActivity<ActivityGameBinding>(
    ActivityGameBinding::inflate
) {

    private var player: ExoPlayer? = null

    override fun onViewBindingCreated(savedInstanceState: Bundle?) {
        super.onViewBindingCreated(savedInstanceState)
        setupControls()
        playSound()

        // Check if we need to reset the game (from GameOver screen)
        if (intent.getBooleanExtra("reset_game", false)) {
            binding.gameView.resetGame()
        }
    }

    private fun playSound() {
        player = ExoPlayer.Builder(this).build().apply {
            val rawUri = "android.resource://${packageName}/${R.raw.rainbowlaser}".toUri()
            val mediaItem = MediaItem.fromUri(rawUri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0.1f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControls() {
        setupHoldButton(binding.btnUp, GameView.Direction.UP)
        setupHoldButton(binding.btnDown, GameView.Direction.DOWN)
        setupHoldButton(binding.btnLeft, GameView.Direction.LEFT)
        setupHoldButton(binding.btnRight, GameView.Direction.RIGHT)

        // Attack buttons
        binding.btnAttackWarrior.setOnClickListener {
            binding.gameView.attackWarrior()
        }
        binding.btnAttackArcher.setOnClickListener {
            binding.gameView.attackArcher()
        }
        binding.btnAttackLancer.setOnClickListener {
            binding.gameView.attackLancer()
        }

        // Guard button (hold to guard)
        binding.btnGuard.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.gameView.startGuarding()
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.gameView.stopGuarding()
                    false
                }
                else -> false
            }
        }

        binding.switchSoundEffect.setOnCheckedChangeListener { _, isChecked ->
            binding.gameView.toggleSound(isChecked)
        }

        binding.switchSoundBackground.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                player?.play()
            } else {
                player?.pause()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHoldButton(button: View, direction: GameView.Direction) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.gameView.startMoving(direction)
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.gameView.stopMoving()
                    false
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (player?.isPlaying == false) {
            player?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (player?.isPlaying == true) {
            player?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
