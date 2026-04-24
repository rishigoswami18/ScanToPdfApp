package com.hrishipvt.scantopdf

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.hrishipvt.scantopdf.utils.AdManager

class ScanToPdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Step 0: Initialize AdMob
        AdManager.init(this)

        // Step 1: Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Step 2: Configure App Check
        /* Temporarily disabled since the API is not yet enabled in Firebase console
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        
        // Use full package name for BuildConfig to avoid import issues
        if (com.hrishipvt.scantopdf.BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
        */

        // Step 3: Subscribe to all_users FCM topic for push notifications
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("ScanToPdfApp", "Subscribed to all_users topic")
                } else {
                    Log.w("ScanToPdfApp", "Failed to subscribe to all_users topic")
                }
            }

        // Step 4: Save FCM token and user info to Firestore
        registerFcmToken()
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val user = FirebaseAuth.getInstance().currentUser ?: return@addOnSuccessListener
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.uid)
                .set(
                    hashMapOf(
                        "fcmToken" to token,
                        "email" to (user.email ?: ""),
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
        }
    }
}
