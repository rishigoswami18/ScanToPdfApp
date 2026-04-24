package com.hrishipvt.scantopdf.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AdManager {
    private const val TAG = "AdManager"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val PREFS_NAME = "AdPrefs"
    private const val LAST_AD_DATE = "last_ad_date"

    private var mInterstitialAd: InterstitialAd? = null

    fun init(context: Context) {
        MobileAds.initialize(context) {}
        loadAd(context)
    }

    private fun loadAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, TEST_INTERSTITIAL_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, adError.message)
                    mInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "Ad was loaded.")
                    mInterstitialAd = interstitialAd
                }
            })
    }

    /**
     * Shows the ad only if it hasn't been shown today.
     */
    fun showAdIfPossible(activity: Activity) {
        if (canShowAdToday(activity)) {
            mInterstitialAd?.let {
                it.show(activity)
                markAdAsShownToday(activity)
                mInterstitialAd = null // Reset after showing
                loadAd(activity) // Pre-load for tomorrow
            } ?: run {
                Log.d(TAG, "The interstitial ad wasn't ready yet.")
                loadAd(activity)
            }
        } else {
            Log.d(TAG, "Ad already shown today. Skipping.")
        }
    }

    private fun canShowAdToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(LAST_AD_DATE, "")
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return lastDate != currentDate
    }

    private fun markAdAsShownToday(context: Context) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LAST_AD_DATE, currentDate)
            .apply()
    }
}
