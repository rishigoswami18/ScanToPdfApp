package com.hrishipvt.scantopdf.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.adapter.PdfAdapter
import java.io.File

class PdfListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_list)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerPdf)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val pdfFiles = downloadsDir.listFiles { file ->
            file.extension.equals("pdf", true)
        }?.toList() ?: emptyList()

        recyclerView.adapter = PdfAdapter(pdfFiles) { selectedFile ->
            sharePdf(selectedFile)
        }

    }
    private fun sharePdf(pdfFile: File) {
        // FIX: 'applicationId' is a build-time constant.
        // Use 'packageName' or your hardcoded package string.
        val authority = "${packageName}.fileprovider"

        val uri = FileProvider.getUriForFile(
            this,
            authority,
            pdfFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Essential for security
        }

        startActivity(Intent.createChooser(intent, "Share PDF using..."))
    }

    // Example of how to "use" the function to clear the warning
    private fun onShareClicked(file: File) {
        sharePdf(file)
    }
}
