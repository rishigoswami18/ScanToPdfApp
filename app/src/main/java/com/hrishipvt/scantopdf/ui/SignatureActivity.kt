package com.hrishipvt.scantopdf.ui

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.view.SignatureView
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SignatureActivity : AppCompatActivity() {

    private var pdfUriString: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signature)

        pdfUriString = intent.getStringExtra("pdf_uri")
        val signaturePad: SignatureView = findViewById(R.id.signaturePad)
        val btnClear: android.widget.Button = findViewById(R.id.btnClear)
        val btnDone: android.widget.Button = findViewById(R.id.btnDone)

        btnClear.setOnClickListener { signaturePad.clear() }

        btnDone.setOnClickListener {
            val bitmap = signaturePad.getSignatureBitmap()
            if (pdfUriString != null) {
                applySignatureToPdf(bitmap, Uri.parse(pdfUriString))
            }
        }
    }

    private fun applySignatureToPdf(signature: Bitmap, uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val document = PDDocument.load(inputStream)
                    val page = document.getPage(0) // Applying to first page

                    val ximage = LosslessFactory.createFromImage(document, signature)

                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true).use { contentStream ->
                        // Position signature at bottom right (adjust x, y as needed)
                        contentStream.drawImage(ximage, 400f, 50f, 150f, 75f)
                    }

                    val outputFile = File(getExternalFilesDir(null), "Signed_${System.currentTimeMillis()}.pdf")
                    val outputStream = FileOutputStream(outputFile)
                    document.save(outputStream)
                    document.close()
                    outputStream.close()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SignatureActivity, "Signed PDF Saved!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignatureActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}