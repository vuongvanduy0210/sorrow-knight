package com.duyvv.sorrow_knight

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.exoplayer.ExoPlayer
import com.duyvv.sorrow_knight.databinding.ActivityGameBinding
import com.duyvv.sorrow_knight.ui.GameView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupControls()
        playSound()
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
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.gameView.stopGuarding()
                    true
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
