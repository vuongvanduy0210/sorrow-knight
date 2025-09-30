package com.duyvv.sorrow_knight

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.duyvv.sorrow_knight.databinding.ActivityGameOverBinding

class GameOverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameOverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityGameOverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
    }

    private fun setupButtons() {
        // Replay button - restart the game
        binding.btnReplay.setOnClickListener {
            // Go back to GameActivity and reset the game
            val intent = Intent(this, GameActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra("reset_game", true)
            startActivity(intent)
            finish()
        }

        // Exit button - go back to splash screen
        binding.btnExit.setOnClickListener {
            val intent = Intent(this, SplashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
