package com.hrishipvt.scantopdf.utils

import android.content.Context
import android.net.Uri
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import java.io.InputStream

object PdfTextUtils {

    /**
     * Extracts text from a PDF file using its Uri.
     */
    fun extractText(context: Context, uri: Uri): String {
        val sb = StringBuilder()
        var inputStream: InputStream? = null
        var reader: PdfReader? = null

        try {
            // 1. Open InputStream from Uri
            inputStream = context.contentResolver.openInputStream(uri) ?: return ""

            // 2. Initialize PdfReader directly from the stream
            reader = PdfReader(inputStream)

            // 3. iTextG uses reader.numberOfPages to get the page count
            val pageCount = reader.numberOfPages

            for (i in 1..pageCount) {
                // 4. Use PdfTextExtractor with the reader and page index
                val pageText = PdfTextExtractor.getTextFromPage(reader, i)
                sb.append(pageText).append("\n")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return "Extraction failed: ${e.localizedMessage}"
        } finally {
            // 5. Always close reader and stream to prevent memory leaks
            reader?.close()
            inputStream?.close()
        }

        return sb.toString()
    }
}