package com.hrishipvt.scantopdf.utils

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import org.apache.poi.xwpf.usermodel.XWPFDocument

object FileUtils {
    fun getBase64FromUri(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            // Encode to Base64 without line breaks for Cloud Functions compatibility
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getTextFromDocx(contentResolver: ContentResolver, uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = XWPFDocument(inputStream)
                val textBuilder = StringBuilder()
                for (paragraph in document.paragraphs) {
                    textBuilder.append(paragraph.text).append("\n")
                }
                textBuilder.toString()
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}