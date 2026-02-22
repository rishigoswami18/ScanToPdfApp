package com.hrishipvt.scantopdf.utils

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

    fun createPdf(
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

        val downloads =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val pdfFile = File(downloads, "Scan_${System.currentTimeMillis()}.pdf")

        FileOutputStream(pdfFile).use { output ->
            pdfDocument.writeTo(output)
        }

        pdfDocument.close()
        return pdfFile
    }
}
