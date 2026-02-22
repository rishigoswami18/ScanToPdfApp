package com.hrishipvt.scantopdf.utils

// Ensure these specific iText imports are used
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.pdf.*
import java.io.FileOutputStream

object PdfService {

    // MERGE: Combine multiple PDFs into one
    fun mergePdfs(srcPaths: List<String>, destPath: String) {
        val document = Document()
        val copy = PdfCopy(document, FileOutputStream(destPath))
        document.open()
        for (path in srcPaths) {
            val reader = PdfReader(path)
            copy.addDocument(reader)
            reader.close()
        }
        document.close()
    }

    // PROTECT: Add password encryption
    fun encryptPdf(src: String, dest: String, userPass: String, ownerPass: String) {
        val reader = PdfReader(src)
        val stamper = PdfStamper(reader, FileOutputStream(dest))
        // Standard encryption settings
        stamper.setEncryption(
            userPass.toByteArray(), ownerPass.toByteArray(),
            PdfWriter.ALLOW_PRINTING, PdfWriter.ENCRYPTION_AES_128
        )
        stamper.close()
        reader.close()
    }

    // WATERMARK: Add semi-transparent branding
    fun addTextWatermark(src: String, dest: String, text: String) {
        val reader = PdfReader(src)
        val stamper = PdfStamper(reader, FileOutputStream(dest))
        val font = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.EMBEDDED)

        // Use PdfGState for transparency to resolve "Unresolved reference"
        val graphicsState = PdfGState()
        graphicsState.setFillOpacity(0.3f) // Explicit setter avoids type inference issues

        for (i in 1..reader.numberOfPages) {
            val over = stamper.getOverContent(i)
            over.saveState() // Maintain graphics state integrity
            over.setGState(graphicsState)
            over.beginText()
            over.setFontAndSize(font, 50f)
            // Center the watermark on the page
            over.showTextAligned(Element.ALIGN_CENTER, text, 297f, 421f, 45f)
            over.endText()
            over.restoreState()
        }
        stamper.close()
        reader.close()
    }
}