package com.hrishipvt.scantopdf.ui

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.hrishipvt.scantopdf.adapter.PdfAdapter
import com.hrishipvt.scantopdf.databinding.ActivityPdfListBinding
import com.hrishipvt.scantopdf.utils.FirebaseUploadUtils
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity
import java.io.File
import java.util.Locale

class PdfListActivity : VoiceEnabledActivity() {

    private lateinit var binding: ActivityPdfListBinding
    private var pdfFiles: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVoiceAssistant()
        setupEmptyStateActions()
        loadPdfFiles()
    }

    override fun voiceCommandHelp(): String {
        return "Try saying status, refresh, open latest PDF, share latest PDF, analyze latest PDF, upload latest PDF, or open followed by part of a file name."
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        val openQuery = textAfterCommand(rawCommand, "open ", "show ")
        val shareQuery = textAfterCommand(rawCommand, "share ")
        val analyzeQuery = textAfterCommand(rawCommand, "analyze ", "summarize ")
        val uploadQuery = textAfterCommand(rawCommand, "upload ")

        return when {
            normalizedCommand.contains("refresh") || normalizedCommand.contains("reload") -> {
                loadPdfFiles()
                speak(documentCountSummary())
                true
            }

            normalizedCommand.contains("status") || normalizedCommand.contains("how many") || normalizedCommand.contains("file count") -> {
                speak(documentCountSummary())
                true
            }

            normalizedCommand.contains("open latest") || normalizedCommand.contains("open newest") -> {
                latestPdf()?.let(::openPdf) ?: speak("There are no PDFs to open.")
                true
            }

            normalizedCommand.contains("share latest") || normalizedCommand.contains("share newest") -> {
                latestPdf()?.let(::sharePdf) ?: speak("There are no PDFs to share.")
                true
            }

            normalizedCommand.contains("analyze latest") || normalizedCommand.contains("summarize latest") -> {
                latestPdf()?.let(::openPdfInAi) ?: speak("There are no PDFs to analyze.")
                true
            }

            normalizedCommand.contains("upload latest") || normalizedCommand.contains("sync latest") -> {
                latestPdf()?.let(::uploadPdf) ?: speak("There are no PDFs to upload.")
                true
            }

            openQuery.isNotEmpty() -> {
                val targetFile = resolvePdfTarget(rawCommand, openQuery)
                if (targetFile != null) {
                    openPdf(targetFile)
                } else {
                    speak("I could not find that PDF.")
                }
                true
            }

            shareQuery.isNotEmpty() -> {
                val targetFile = resolvePdfTarget(rawCommand, shareQuery)
                if (targetFile != null) {
                    sharePdf(targetFile)
                } else {
                    speak("I could not find that PDF to share.")
                }
                true
            }

            analyzeQuery.isNotEmpty() -> {
                val targetFile = resolvePdfTarget(rawCommand, analyzeQuery)
                if (targetFile != null) {
                    openPdfInAi(targetFile)
                } else {
                    speak("I could not find that PDF to analyze.")
                }
                true
            }

            uploadQuery.isNotEmpty() -> {
                val targetFile = resolvePdfTarget(rawCommand, uploadQuery)
                if (targetFile != null) {
                    uploadPdf(targetFile)
                } else {
                    speak("I could not find that PDF to upload.")
                }
                true
            }

            else -> false
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupEmptyStateActions() {
        binding.btnEmptyScan.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        binding.btnEmptyAi.setOnClickListener {
            startActivity(Intent(this, AiChatActivity::class.java))
        }
    }

    private fun loadPdfFiles() {
        val pdfByPath = linkedMapOf<String, File>()
        val locations = listOf(
            getExternalFilesDir(null),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        locations.forEach { directory ->
            directory?.listFiles { file ->
                file.extension.equals("pdf", true)
            }?.forEach { file ->
                pdfByPath[file.absolutePath] = file
            }
        }

        pdfFiles = pdfByPath.values.sortedByDescending { it.lastModified() }

        if (pdfFiles.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerPdf.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.recyclerPdf.visibility = View.VISIBLE

            binding.recyclerPdf.apply {
                layoutManager = LinearLayoutManager(this@PdfListActivity)
                adapter = PdfAdapter(pdfFiles) { selectedFile ->
                    sharePdf(selectedFile)
                }
            }
        }
    }

    private fun sharePdf(pdfFile: File) {
        val authority = "${packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(this, authority, pdfFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    private fun openPdf(pdfFile: File) {
        val authority = "${packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(this, authority, pdfFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No PDF viewer found", Toast.LENGTH_SHORT).show()
            speak("No PDF viewer is installed on this device.")
        }
    }

    private fun openPdfInAi(pdfFile: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", pdfFile)
        val intent = Intent(this, AiChatActivity::class.java).apply {
            putExtra("pdf_uri_from_list", uri)
        }
        startActivity(intent)
    }

    private fun uploadPdf(pdfFile: File) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(this, "Please login to upload files", Toast.LENGTH_SHORT).show()
            speak("Please sign in before uploading files to the cloud.")
            return
        }

        FirebaseUploadUtils.uploadPdfToCloud(pdfFile.absolutePath) { success, message ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Uploaded successfully!", Toast.LENGTH_SHORT).show()
                    speak("${pdfFile.name} uploaded successfully.")
                } else {
                    Toast.makeText(this, "Error: $message", Toast.LENGTH_SHORT).show()
                    speak("I could not upload that PDF right now.")
                }
            }
        }
    }

    private fun latestPdf(): File? = pdfFiles.maxByOrNull { it.lastModified() }

    private fun resolvePdfTarget(rawCommand: String, query: String): File? {
        if (pdfFiles.isEmpty()) return null

        return when {
            rawCommand.contains("latest", ignoreCase = true) || rawCommand.contains("newest", ignoreCase = true) -> latestPdf()
            rawCommand.contains("first", ignoreCase = true) -> pdfFiles.firstOrNull()
            else -> {
                val cleanedQuery = sanitizeQuery(query)
                if (cleanedQuery.isBlank()) null
                else pdfFiles.firstOrNull { it.name.lowercase(Locale.getDefault()).contains(cleanedQuery) }
            }
        }
    }

    private fun sanitizeQuery(query: String): String {
        return query.lowercase(Locale.getDefault())
            .replace("pdf", "")
            .replace("file", "")
            .replace("document", "")
            .replace("please", "")
            .trim()
    }

    private fun documentCountSummary(): String {
        return if (pdfFiles.isEmpty()) {
            "No PDFs were found in your downloads folder."
        } else {
            "You have ${pdfFiles.size} PDF files available."
        }
    }
}
