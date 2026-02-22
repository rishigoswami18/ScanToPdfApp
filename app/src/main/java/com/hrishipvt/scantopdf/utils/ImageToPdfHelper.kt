package com.hrishipvt.scantopdf.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
// Core iTextG imports - DO NOT use 'kernel' or 'io'
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.pdf.PdfWriter
// Standard Java IO imports
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageToPdfHelper {

    /**
     * Converts a single image file to a PDF document.
     */
    fun convertImageToPdf(imagePath: String, pdfPath: String) {
        // Initialize iText Document (using A4 standard)
        val document = Document(PageSize.A4)

        try {
            // Initialize PdfWriter
            PdfWriter.getInstance(document, FileOutputStream(pdfPath))

            // Open the document for writing
            document.open()

            // Load Bitmap and convert to iText Image
            val bitmap = BitmapFactory.decodeFile(imagePath)
            val stream = ByteArrayOutputStream()

            // Compress bitmap into the stream
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val img = Image.getInstance(stream.toByteArray())

            // Calculate Scaling to fit within page margins
            val width = document.pageSize.width - document.leftMargin() - document.rightMargin()
            val height = document.pageSize.height - document.topMargin() - document.bottomMargin()
            img.scaleToFit(width, height)

            // Set Alignment to center
            img.alignment = Image.ALIGN_CENTER

            // Add image to document
            document.add(img)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Safely close the document
            if (document.isOpen) {
                document.close()
            }
        }
    }
}