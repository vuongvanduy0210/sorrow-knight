package com.duyvv.sorrow_knight

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.addCallback
import androidx.recyclerview.widget.LinearLayoutManager
import com.duyvv.sorrow_knight.base.BaseActivity
import com.duyvv.sorrow_knight.databinding.ActivityLeaderboardBinding
import com.duyvv.sorrow_knight.ui.LeaderboardAdapter
import org.json.JSONArray
import org.json.JSONObject

class LeaderboardActivity : BaseActivity<ActivityLeaderboardBinding>(
    ActivityLeaderboardBinding::inflate
) {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var leaderboardAdapter: LeaderboardAdapter

    override fun onViewBindingCreated(savedInstanceState: Bundle?) {
        super.onViewBindingCreated(savedInstanceState)
        
        sharedPreferences = getSharedPreferences("game_scores", Context.MODE_PRIVATE)
        
        setupLeaderboard()
        setupButtons()
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

    private fun setupLeaderboard() {
        val scores = getScores()
        leaderboardAdapter = LeaderboardAdapter(scores)
        
        binding.recyclerViewLeaderboard.apply {
            layoutManager = LinearLayoutManager(this@LeaderboardActivity)
            adapter = leaderboardAdapter
        }
        
        // Show empty state if no scores
        if (scores.isEmpty()) {
            binding.tvEmptyState.visibility = android.view.View.VISIBLE
            binding.recyclerViewLeaderboard.visibility = android.view.View.GONE
        } else {
            binding.tvEmptyState.visibility = android.view.View.GONE
            binding.recyclerViewLeaderboard.visibility = android.view.View.VISIBLE
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnClearScores.setOnClickListener {
            clearAllScores()
        }
        
        binding.btnPlayGame.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra("reset_game", true)
            startActivity(intent)
            finish()
        }
    }
    
    private fun clearAllScores() {
        sharedPreferences.edit()
            .remove("scores")
            .apply()
        
        // Refresh the leaderboard
        setupLeaderboard()
    }
}
