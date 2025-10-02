package com.duyvv.sorrow_knight

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import com.duyvv.sorrow_knight.base.BaseActivity
import com.duyvv.sorrow_knight.databinding.ActivityVictoryBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class VictoryActivity : BaseActivity<ActivityVictoryBinding>(
    ActivityVictoryBinding::inflate
) {

    private lateinit var sharedPreferences: SharedPreferences
    override fun onViewBindingCreated(savedInstanceState: Bundle?) {
        super.onViewBindingCreated(savedInstanceState)
        
        sharedPreferences = getSharedPreferences("game_scores", Context.MODE_PRIVATE)
        
        val gameTime = intent.getLongExtra("game_time", 0L)
        val enemiesKilled = intent.getIntExtra("enemies_killed", 0)
        
        saveScore(gameTime, enemiesKilled)
        setupUI(gameTime, enemiesKilled)
        setupButtons()
    }

    private fun saveScore(gameTime: Long, enemiesKilled: Int) {
        val scores = getScores().toMutableList()
        
        val newScore = JSONObject().apply {
            put("time", gameTime)
            put("kills", enemiesKilled)
            put("date", System.currentTimeMillis())
        }
        
        scores.add(newScore)
        
        // Sort by time (ascending - faster is better)
        scores.sortBy { it.getLong("time") }
        
        // Keep only top 10 scores
        val topScores = scores.take(10)
        
        val jsonArray = JSONArray()
        topScores.forEach { jsonArray.put(it) }
        
        sharedPreferences.edit()
            .putString("scores", jsonArray.toString())
            .apply()
    }

    private fun getScores(): List<JSONObject> {
        val scoresString = sharedPreferences.getString("scores", "[]") ?: "[]"
        val jsonArray = JSONArray(scoresString)
        val scores = mutableListOf<JSONObject>()
        
        for (i in 0 until jsonArray.length()) {
            scores.add(jsonArray.getJSONObject(i))
        }
        
        return scores
    }

    private fun setupUI(gameTime: Long, enemiesKilled: Int) {
        val minutes = gameTime / 60000
        val seconds = (gameTime % 60000) / 1000
        val timeString = String.format("%02d:%02d", minutes, seconds)
        
        binding.tvGameTime.text = "Time: $timeString"
        binding.tvKillCount.text = "Enemies Killed: $enemiesKilled"
    }

    private fun setupButtons() {
        binding.btnPlayAgain.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra("reset_game", true)
            startActivity(intent)
            finish()
        }

        binding.btnViewLeaderboard.setOnClickListener {
            val intent = Intent(this, LeaderboardActivity::class.java)
            startActivity(intent)
        }

        binding.btnMainMenu.setOnClickListener {
            val intent = Intent(this, SplashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
