package com.hrishipvt.scantopdf.ui

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.hrishipvt.scantopdf.databinding.ActivityMergePdfBinding
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MergePdfActivity : VoiceEnabledActivity() {

    private lateinit var binding: ActivityMergePdfBinding
    private val selectedUris = mutableListOf<Uri>()
    private var shouldSpeakMergeResult = false

    private val pickPdfLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                selectedUris.clear()
                selectedUris.addAll(uris)
                updateSelectionStatus()
                speak("${selectedUris.size} PDF files selected.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMergePdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PDFBoxResourceLoader.init(applicationContext)
        setupToolbar()
        setupVoiceAssistant()
        updateSelectionStatus()
        setupListeners()
    }

    override fun voiceCommandHelp(): String {
        return "Try saying choose files, merge PDFs, clear selection, or status."
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        return when {
            normalizedCommand.contains("choose") || normalizedCommand.contains("select") || normalizedCommand.contains("pick files") -> {
                speak("Choose the PDF files you want to merge.")
                pickPdfLauncher.launch(arrayOf("application/pdf"))
                true
            }

            normalizedCommand.contains("merge") || normalizedCommand.contains("combine") -> {
                if (selectedUris.size < 2) {
                    speak("Please select at least two PDF files first.")
                } else {
                    shouldSpeakMergeResult = true
                    mergePdfs()
                }
                true
            }

            normalizedCommand.contains("clear selection") || normalizedCommand.contains("reset selection") -> {
                selectedUris.clear()
                updateSelectionStatus()
                speak("Selection cleared.")
                true
            }

            normalizedCommand.contains("status") || normalizedCommand.contains("how many") -> {
                val summary = if (selectedUris.isEmpty()) {
                    "No PDF files are selected yet."
                } else {
                    "${selectedUris.size} PDF files are ready to merge."
                }
                speak(summary)
                true
            }

            else -> false
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupListeners() {
        binding.btnSelectPdf.setOnClickListener {
            pickPdfLauncher.launch(arrayOf("application/pdf"))
        }

        binding.btnMergePdf.setOnClickListener {
            if (selectedUris.size < 2) {
                Toast.makeText(this, "Select at least 2 PDFs to merge", Toast.LENGTH_SHORT).show()
            } else {
                shouldSpeakMergeResult = false
                mergePdfs()
            }
        }
    }

    private fun updateSelectionStatus() {
        binding.txtSelectionStatus.text = if (selectedUris.isEmpty()) {
            "No files selected"
        } else {
            "${selectedUris.size} PDFs selected"
        }
    }

    private fun mergePdfs() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnMergePdf.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var outputDocument: PDDocument? = null
            try {
                val merger = PDFMergerUtility()
                val mergedDocument = PDDocument()
                outputDocument = mergedDocument

                selectedUris.forEach { uri ->
                    contentResolver.openInputStream(uri)?.use { input ->
                        val doc = PDDocument.load(input)
                        merger.appendDocument(mergedDocument, doc)
                        doc.close()
                    }
                }

                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, "Merged_${System.currentTimeMillis()}.pdf")

                mergedDocument.save(file)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnMergePdf.isEnabled = true
                    Toast.makeText(this@MergePdfActivity, "PDFs merged successfully! Check Downloads.", Toast.LENGTH_LONG).show()
                    selectedUris.clear()
                    updateSelectionStatus()
                    if (shouldSpeakMergeResult) {
                        shouldSpeakMergeResult = false
                        speak("Your PDFs were merged successfully. Check the downloads folder.")
                    }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnMergePdf.isEnabled = true
                    Toast.makeText(this@MergePdfActivity, "Merge failed: ${error.message}", Toast.LENGTH_SHORT).show()
                    if (shouldSpeakMergeResult) {
                        shouldSpeakMergeResult = false
                        speak("The merge failed. Please try again.")
                    }
                }
            } finally {
                outputDocument?.close()
            }
        }
    }
}
