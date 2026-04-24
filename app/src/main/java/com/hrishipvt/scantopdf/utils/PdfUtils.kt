package com.hrishipvt.scantopdf.utils

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import com.google.firebase.auth.FirebaseAuth

object PdfUtils {

    fun createPdf(
        context: Context,
        pages: List<Bitmap>,
        password: String? = null // reserved for future (premium)
    ): File {

        val pdfDocument = PdfDocument()

        pages.forEachIndexed { index, bitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(
                bitmap.width,
                bitmap.height,
                index + 1
            ).create()

            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)
        }

        val isolatedDir = getIsolatedPdfDirectory(context)

        val pdfFile = File(isolatedDir, "Scan_${System.currentTimeMillis()}.pdf")

        FileOutputStream(pdfFile).use { output ->
            pdfDocument.writeTo(output)
        }

        pdfDocument.close()
        return pdfFile
    }

    fun getIsolatedPdfDirectory(context: Context): File {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val isolatedDir = File(baseDir, "Scans/$userId")
        if (!isolatedDir.exists()) {
            isolatedDir.mkdirs()
        }
        return isolatedDir
    }
}
