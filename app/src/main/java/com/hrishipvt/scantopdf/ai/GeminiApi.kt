package com.hrishipvt.scantopdf.ai

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiApi {
    private const val TAG = "GeminiApi"

    // Groq API — 100% free, no billing needed
    private const val API_KEY = "YOUR_GROQ_API_KEY_HERE"
    private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val TEXT_MODEL = "llama-3.3-70b-versatile"
    private const val VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"

    private const val SYSTEM_PROMPT = "You are the AI Assistant for 'SmartScan', developed by Hrishikesh Giri. Be professional and concise."

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun chatWithFile(
        userMessage: String,
        fileUri: Uri?,
        contentResolver: ContentResolver,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Sending chat request: ${userMessage.take(100)}...")

                val message = userMessage.ifEmpty { "Analyze this file." }
                val result: String

                if (fileUri != null) {
                    val mimeType = contentResolver.getType(fileUri) ?: "application/octet-stream"

                    if (mimeType.startsWith("image/")) {
                        // Image file → use vision model
                        val fileBytes = contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                        if (fileBytes != null) {
                            val base64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                            result = callGroqVision(message, base64, mimeType)
                        } else {
                            result = callGroqText(message)
                        }
                    } else {
                        // Non-image file → extract text if possible
                        val textContent = try {
                            contentResolver.openInputStream(fileUri)?.use {
                                it.bufferedReader().readText().take(8000)
                            } ?: ""
                        } catch (e: Exception) { "" }

                        val fullMessage = if (textContent.isNotEmpty()) {
                            "$message\n\nFile content:\n$textContent"
                        } else {
                            message
                        }
                        result = callGroqText(fullMessage)
                    }
                } else {
                    result = callGroqText(message)
                }

                withContext(Dispatchers.Main) {
                    if (result.isBlank()) {
                        Log.e(TAG, "AI returned empty response")
                        onError("AI returned an empty response.")
                    } else {
                        Log.d(TAG, "AI response received: ${result.take(100)}...")
                        onSuccess(result)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chat error: ${e.javaClass.simpleName}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError("AI Error: ${e.localizedMessage ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    fun processAiTask(
        prompt: String,
        content: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fullMessage = "$prompt\n\nContent:\n$content"
                Log.d(TAG, "Processing AI task: ${prompt.take(100)}...")
                val result = callGroqText(fullMessage)

                withContext(Dispatchers.Main) {
                    if (result.isBlank()) {
                        Log.e(TAG, "AI task returned empty response")
                        onError("AI returned an empty response.")
                    } else {
                        Log.d(TAG, "AI task response: ${result.take(100)}...")
                        onSuccess(result)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI task error: ${e.javaClass.simpleName}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError("AI Error: ${e.localizedMessage ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    private fun callGroqText(userMessage: String): String {
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val body = JSONObject().apply {
            put("model", TEXT_MODEL)
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("max_tokens", 2048)
        }

        return executeRequest(body)
    }

    private fun callGroqVision(userMessage: String, base64Image: String, mimeType: String): String {
        val contentArray = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "text")
                put("text", userMessage)
            })
            put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:$mimeType;base64,$base64Image")
                })
            })
        }

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", contentArray)
            })
        }

        val body = JSONObject().apply {
            put("model", VISION_MODEL)
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("max_tokens", 2048)
        }

        return executeRequest(body)
    }

    private fun executeRequest(body: JSONObject): String {
        val request = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e(TAG, "API error ${response.code}: $responseBody")
            throw RuntimeException("API error ${response.code}: ${response.message}")
        }

        val json = JSONObject(responseBody)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
}
