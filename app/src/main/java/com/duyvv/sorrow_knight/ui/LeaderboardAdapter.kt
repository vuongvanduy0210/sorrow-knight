package com.duyvv.sorrow_knight.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duyvv.sorrow_knight.databinding.ItemLeaderboardBinding
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class LeaderboardAdapter(private val scores: List<JSONObject>) : 
    RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {

    class LeaderboardViewHolder(private val binding: ItemLeaderboardBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(score: JSONObject, position: Int) {
            val time = score.getLong("time")
            val kills = score.getInt("kills")
            val date = score.getLong("date")
            
            val minutes = time / 60000
            val seconds = (time % 60000) / 1000
            val timeString = String.format("%02d:%02d", minutes, seconds)
            
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dateString = dateFormat.format(Date(date))
            
            binding.tvRank.text = "#${position + 1}"
            binding.tvTime.text = timeString
            binding.tvKills.text = "$kills kills"
            binding.tvDate.text = dateString
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val binding = ItemLeaderboardBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return LeaderboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        holder.bind(scores[position], position)
    }

    override fun getItemCount(): Int = scores.size
}
