package com.hrishipvt.scantopdf.ui

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.hrishipvt.scantopdf.R

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val centerContainer = findViewById<LinearLayout>(R.id.centerContainer)

        val fadeIn = AlphaAnimation(0f, 1f).apply {
            interpolator = DecelerateInterpolator()
            duration = 1000
        }

        val slideUp = TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 0.2f, Animation.RELATIVE_TO_SELF, 0f
        ).apply {
            interpolator = DecelerateInterpolator()
            duration = 1000
        }

        val animationSet = AnimationSet(false).apply {
            addAnimation(fadeIn)
            addAnimation(slideUp)
        }

        centerContainer.startAnimation(animationSet)

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            val options = ActivityOptions.makeCustomAnimation(
                this, 
                android.R.anim.fade_in, 
                android.R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            finish()
        }, 2000)
    }
}
