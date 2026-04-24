package com.hrishipvt.scantopdf.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enterprise-grade PDF Generation Engine.
 * Features: A4 Standardized, Multi-page intelligent pagination, Typeface optimized,
 * Automated Metadata, and Footer tracking.
 */
object NotePdfUtils {

    // A4 Paper Size at 72 DPI (Standard)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 50f

    fun createPdf(context: Context, title: String, content: String): File? {
        val pdfDocument = PdfDocument()
        try {
            val bitmaps = renderTextToBitmaps(title, content)
            if (bitmaps.isEmpty()) return null

            bitmaps.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)
            }

            val safeTitle = title.ifEmpty { "Note" }.replace(Regex("[^a-zA-Z0-9]"), "_")
            val fileName = "DOC_${safeTitle}_${System.currentTimeMillis()}.pdf"
            
            // Save in isolated directory
            val outputDirectory = PdfUtils.getIsolatedPdfDirectory(context)
            val file = File(outputDirectory, fileName)
            
            FileOutputStream(file).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            pdfDocument.close()
        }
    }

    fun renderTextToBitmaps(title: String, content: String): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        
        val titlePaint = TextPaint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 24f
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }

        val contentPaint = TextPaint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 13f
            color = android.graphics.Color.parseColor("#333333")
            isAntiAlias = true
        }

        val usableWidth = PAGE_WIDTH - (MARGIN * 2)
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, usableWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .build()

        val contentLayout = StaticLayout.Builder.obtain(content, 0, content.length, contentPaint, usableWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .build()

        var currentPageNumber = 1
        var currentY = MARGIN

        var currentBitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        var canvas = Canvas(currentBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        // Draw Title
        canvas.save()
        canvas.translate(MARGIN, currentY)
        titleLayout.draw(canvas)
        canvas.restore()
        currentY += titleLayout.height + 30f

        val lineCount = contentLayout.lineCount
        for (i in 0 until lineCount) {
            val lineHeight = (contentLayout.getLineBottom(i) - contentLayout.getLineTop(i)).toFloat()

            if (currentY + lineHeight > PAGE_HEIGHT - MARGIN - 60f) {
                drawFooter(canvas, currentPageNumber)
                bitmaps.add(currentBitmap)
                
                currentPageNumber++
                currentBitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
                canvas = Canvas(currentBitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                currentY = MARGIN
            }

            canvas.save()
            // Translate to the margin and current Y
            canvas.translate(MARGIN, currentY)
            // Clip to only draw this specific line from the StaticLayout. 
            // We use 0 as the top of the clip relative to currentY.
            canvas.clipRect(0f, 0f, usableWidth, lineHeight)
            // Offset the draw by the line's top within the layout so it draws at our current translation
            canvas.translate(0f, -contentLayout.getLineTop(i).toFloat())
            contentLayout.draw(canvas)
            canvas.restore()
            currentY += lineHeight
        }

        drawFooter(canvas, currentPageNumber)
        bitmaps.add(currentBitmap)
        
        return bitmaps
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int) {
        val footerPaint = Paint().apply {
            textSize = 10f
            color = android.graphics.Color.GRAY
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            isAntiAlias = true
        }
        
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Digital Export | $timestamp", MARGIN, PAGE_HEIGHT - 30f, footerPaint)
        
        val pageIndicator = "Page $pageNum"
        val textWidth = footerPaint.measureText(pageIndicator)
        canvas.drawText(pageIndicator, PAGE_WIDTH - MARGIN - textWidth, PAGE_HEIGHT - 30f, footerPaint)
        
        // Professional Divider Line
        val linePaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 45f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 45f, linePaint)
    }
}
