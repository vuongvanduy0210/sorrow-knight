package com.duyvv.sorrow_knight

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.duyvv.sorrow_knight.base.BaseActivity
import com.duyvv.sorrow_knight.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity<ActivitySplashBinding>(
    ActivitySplashBinding::inflate
) {

    override fun onViewBindingCreated(savedInstanceState: Bundle?) {
        super.onViewBindingCreated(savedInstanceState)
        binding.appName.setOnClickListener {
            startActivity(Intent(this, GameActivity::class.java))
            finish()
        }
    }
}