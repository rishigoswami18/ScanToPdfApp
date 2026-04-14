package com.hrishipvt.scantopdf.utils

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedReader
import java.io.InputStreamReader

object FileUtils {
    fun getBase64FromUri(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
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

    fun getTextFromPdf(contentResolver: ContentResolver, uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = PDDocument.load(inputStream)
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                document.close()
                text
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun readTextFromUri(contentResolver: ContentResolver, uri: Uri): String {
        val stringBuilder = StringBuilder()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line).append("\n")
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return stringBuilder.toString()
    }
}