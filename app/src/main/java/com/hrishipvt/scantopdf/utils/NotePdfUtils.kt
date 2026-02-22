package com.hrishipvt.scantopdf.utils

import android.content.Context
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object NotePdfUtils {

    fun createPdf(context: Context, title: String, content: String): File {

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(600, 800, 1).create()
        val page = pdf.startPage(pageInfo)

        val canvas = page.canvas
        val paint = android.graphics.Paint()
        paint.textSize = 18f

        canvas.drawText(title, 40f, 60f, paint)

        paint.textSize = 14f
        canvas.drawText(content, 40f, 120f, paint)

        pdf.finishPage(page)

        val file = File(context.getExternalFilesDir(null), "$title.pdf")
        pdf.writeTo(FileOutputStream(file))
        pdf.close()

        return file
    }
}
