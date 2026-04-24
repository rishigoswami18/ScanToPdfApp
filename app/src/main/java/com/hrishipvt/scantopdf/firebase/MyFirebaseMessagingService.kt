package com.hrishipvt.scantopdf.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.data.NotificationDatabase
import com.hrishipvt.scantopdf.data.NotificationEntity
import com.hrishipvt.scantopdf.ui.NotificationsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title: String
        val body: String

        if (remoteMessage.notification != null) {
            title = remoteMessage.notification?.title ?: "ScanToPdf"
            body = remoteMessage.notification?.body ?: ""
        } else {
            title = remoteMessage.data["title"] ?: "ScanToPdf"
            body = remoteMessage.data["message"] ?: ""
        }

        // Save to Room database for in-app notifications screen
        saveNotificationToDb(title, body)

        // Show system notification
        showNotification(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToFirestore(token)
    }

    private fun saveNotificationToDb(title: String, body: String) {
        val db = NotificationDatabase.getDatabase(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            db.notificationDao().insert(
                NotificationEntity(
                    title = title,
                    message = body,
                    timestamp = System.currentTimeMillis(),
                    isRead = false
                )
            )
        }
    }

    private fun saveTokenToFirestore(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .set(
                hashMapOf(
                    "fcmToken" to token,
                    "email" to (user.email ?: ""),
                    "updatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
    }

    private fun showNotification(title: String, messageBody: String) {
        // Opens NotificationsActivity when tapped
        val intent = Intent(this, NotificationsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "admin_notifications"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notifications_bell)
            .setColor(getColor(R.color.primary_warm))
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Admin Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications sent by the admin"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
