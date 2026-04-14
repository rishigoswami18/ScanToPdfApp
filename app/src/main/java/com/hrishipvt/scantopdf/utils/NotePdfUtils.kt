package com.hrishipvt.scantopdf.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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
        
        // Optimized Typography for Readability
        val titlePaint = TextPaint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 22f
            color = android.graphics.Color.parseColor("#3E2723") // Theme Brown
            isAntiAlias = true
        }

        val contentPaint = TextPaint().apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 12f
            color = android.graphics.Color.parseColor("#424242")
            isAntiAlias = true
        }

        try {
            val usableWidth = PAGE_WIDTH - (MARGIN * 2)
            
            // Dynamic Layout Engines
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

            // Initialize Master Page
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            // Header/Title Rendering
            canvas.save()
            canvas.translate(MARGIN, currentY)
            titleLayout.draw(canvas)
            canvas.restore()
            currentY += titleLayout.height + 30f // Vertical rhythm spacing

            // Intelligent Multi-page Content Streaming
            val lineCount = contentLayout.lineCount
            for (i in 0 until lineCount) {
                val lineTop = contentLayout.getLineTop(i)
                val lineBottom = contentLayout.getLineBottom(i)
                val lineHeight = (lineBottom - lineTop).toFloat()

                // Page Overflow Detection & Handler
                if (currentY + lineHeight > PAGE_HEIGHT - MARGIN - 50f) {
                    drawFooter(canvas, currentPageNumber)
                    pdfDocument.finishPage(page)
                    
                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = MARGIN
                }

                canvas.save()
                canvas.translate(MARGIN, currentY)
                
                // Content Line Injection
                val start = contentLayout.getLineStart(i)
                val end = contentLayout.getLineEnd(i)
                val lineText = content.substring(start, end).trim()
                if (lineText.isNotEmpty()) {
                    canvas.drawText(lineText, 0f, lineHeight, contentPaint)
                }
                
                canvas.restore()
                currentY += lineHeight
            }

            drawFooter(canvas, currentPageNumber)
            pdfDocument.finishPage(page)

            // Secure File System Persistance
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
            val fileName = "DOC_${safeTitle}_${System.currentTimeMillis()}.pdf"
            val outputDirectory = context.getExternalFilesDir(null) ?: context.filesDir
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
