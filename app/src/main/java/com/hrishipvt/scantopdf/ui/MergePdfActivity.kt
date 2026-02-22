package com.hrishipvt.scantopdf.ui

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.hrishipvt.scantopdf.R
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument

import java.io.File

class MergePdfActivity : AppCompatActivity() {

    private val selectedUris = mutableListOf<Uri>()

    private val pickPdfLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            selectedUris.clear()
            selectedUris.addAll(uris)
            Toast.makeText(this, "${selectedUris.size} PDFs selected", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_merge_pdf)

        // ✅ REQUIRED — without this PDFBox WILL NOT WORK
        PDFBoxResourceLoader.init(applicationContext)

        val btnSelect = findViewById<MaterialButton>(R.id.btnSelectPdf)
        val btnMerge = findViewById<MaterialButton>(R.id.btnMergePdf)

        btnSelect.setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }

        btnMerge.setOnClickListener {
            if (selectedUris.size < 2) {
                Toast.makeText(this, "Select at least 2 PDFs", Toast.LENGTH_SHORT).show()
            } else {
                mergePdfs()
            }
        }
    }

    private fun mergePdfs() {
        try {
            val merger = PDFMergerUtility()
            val outputDocument = PDDocument()

            selectedUris.forEach { uri ->
                contentResolver.openInputStream(uri)?.use { input ->
                    val doc = PDDocument.load(input)
                    merger.appendDocument(outputDocument, doc)
                    doc.close()
                }
            }

            val dir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "Merged_${System.currentTimeMillis()}.pdf")

            outputDocument.save(file)
            outputDocument.close()

            Toast.makeText(this, "Merged PDF saved to Downloads", Toast.LENGTH_LONG).show()
            finish()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Merge failed", Toast.LENGTH_SHORT).show()
        }
    }
}
