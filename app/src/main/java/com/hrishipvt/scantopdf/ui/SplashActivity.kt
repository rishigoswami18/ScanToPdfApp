package com.hrishipvt.scantopdf.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
 // Replace with your actual Main Activity import
import com.hrishipvt.scantopdf.R

// "SuppressLint" is needed because custom splash screens are technically
// discouraged in Android 12+, but this is the easiest way to get the exact look you want.
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Wait for 2 seconds (2000ms) then move to main activity
        Handler(Looper.getMainLooper()).postDelayed({
            // Start Main Activity
            // Assuming your main activity is called MainActivity or AiSummaryActivity
            // Adjust the class name below to match where you want to go next.
            startActivity(Intent(this, MainActivity::class.java))

            // Close the splash activity so the user can't press "back" to return to it
            finish()
        }, 2000)
    }
}