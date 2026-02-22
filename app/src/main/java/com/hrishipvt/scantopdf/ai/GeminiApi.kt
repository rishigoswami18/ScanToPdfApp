package com.hrishipvt.scantopdf.ai

import android.content.ContentResolver
import android.net.Uri
import com.google.firebase.functions.FirebaseFunctions
import com.hrishipvt.scantopdf.utils.FileUtils


object GeminiApi {
    fun chatWithFile(
        userMessage: String,
        fileUri: Uri?,
        contentResolver: ContentResolver,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val functions = FirebaseFunctions.getInstance("us-central1")

        val base64Data = fileUri?.let { FileUtils.getBase64FromUri(contentResolver, it) }
        val mimeType = fileUri?.let { contentResolver.getType(it) }

        val data = hashMapOf(
            "text" to userMessage,
            "fileBase64" to base64Data,
            "mimeType" to mimeType,
            "isChat" to true
        )

        functions.getHttpsCallable("summarizePdf")
            .call(data)
            .addOnSuccessListener { result ->
                val resultData = result.data as? Map<*, *>
                onSuccess(resultData?.get("summary") as? String ?: "No response")
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "AI error")
            }
    }
}