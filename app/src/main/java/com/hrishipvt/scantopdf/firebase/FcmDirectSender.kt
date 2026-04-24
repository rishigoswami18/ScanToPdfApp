package com.hrishipvt.scantopdf.firebase

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

object FcmDirectSender {
    private val client = OkHttpClient()

    suspend fun sendNotification(context: Context, title: String, message: String, adminEmail: String) = withContext(Dispatchers.IO) {
        try {
            // 1. Read Service Account
            val inputStream = context.assets.open("service-account.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val json = JSONObject(String(buffer, Charsets.UTF_8))
            
            val clientEmail = json.getString("client_email")
            val privateKeyStr = json.getString("private_key")
                .replace("-----BEGIN PRIVATE KEY-----\n", "")
                .replace("-----END PRIVATE KEY-----\n", "")
                .replace("\n", "")

            // 2. Generate JWT
            val header = Base64.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val iat = System.currentTimeMillis() / 1000
            val exp = iat + 3600
            val payloadStr = """{"iss":"$clientEmail","scope":"https://www.googleapis.com/auth/firebase.messaging","aud":"https://oauth2.googleapis.com/token","exp":$exp,"iat":$iat}"""
            val payload = Base64.encodeToString(payloadStr.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

            val keyBytes = Base64.decode(privateKeyStr, Base64.DEFAULT)
            val spec = PKCS8EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("RSA")
            val privateKey = kf.generatePrivate(spec)

            val signatureData = "$header.$payload".toByteArray()
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(signatureData)
            val sigString = Base64.encodeToString(signature.sign(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

            val jwt = "$header.$payload.$sigString"

            // 3. Get OAuth Token
            val tokenRequest = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post("grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            val tokenResponse = client.newCall(tokenRequest).execute()
            if (!tokenResponse.isSuccessful) throw Exception("Failed to get OAuth token: ${tokenResponse.body?.string()}")
            val accessToken = JSONObject(tokenResponse.body!!.string()).getString("access_token")

            // 4. Send FCM
            val projectId = json.getString("project_id")
            val fcmUrl = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"
            
            val fcmPayload = JSONObject().apply {
                put("message", JSONObject().apply {
                    put("topic", "all_users")
                    put("notification", JSONObject().apply {
                        put("title", title)
                        put("body", message)
                    })
                    put("data", JSONObject().apply {
                        put("title", title)
                        put("message", message)
                        put("timestamp", System.currentTimeMillis().toString())
                    })
                    put("android", JSONObject().apply {
                        put("priority", "high")
                        put("notification", JSONObject().apply {
                            put("channel_id", "admin_notifications")
                            put("default_sound", true)
                        })
                    })
                })
            }

            val fcmRequest = Request.Builder()
                .url(fcmUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(fcmPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val fcmResponse = client.newCall(fcmRequest).execute()
            val fcmBody = fcmResponse.body?.string()
            if (!fcmResponse.isSuccessful) throw Exception("Failed to send FCM: $fcmBody")

            val messageId = JSONObject(fcmBody ?: "{}").optString("name", "unknown")

            // 5. Save to Firestore
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("notifications").add(
                hashMapOf(
                    "title" to title,
                    "message" to message,
                    "sentBy" to adminEmail,
                    "timestamp" to System.currentTimeMillis(),
                    "messageId" to messageId
                )
            )

            messageId

        } catch (e: Exception) {
            Log.e("FcmDirectSender", "Error sending FCM", e)
            throw e
        }
    }
}
