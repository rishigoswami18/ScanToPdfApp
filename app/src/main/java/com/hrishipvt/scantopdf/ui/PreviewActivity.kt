package com.hrishipvt.scantopdf.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.adapter.EditToolsAdapter
import com.hrishipvt.scantopdf.adapter.PreviewPagerAdapter
import com.hrishipvt.scantopdf.ai.AiOcrUtils
import com.hrishipvt.scantopdf.databinding.ActivityPreviewBinding
import com.hrishipvt.scantopdf.utils.FileUtils
import com.hrishipvt.scantopdf.utils.ImageUtils
import com.hrishipvt.scantopdf.utils.ScanSession
import com.hrishipvt.scantopdf.view.EditTool
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.util.Locale

class PreviewActivity : VoiceEnabledActivity() {

    private lateinit var binding: ActivityPreviewBinding
    private lateinit var adapter: PreviewPagerAdapter
    private val extractedText = StringBuilder()
    private var customTitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVoiceAssistant()
        initViews()
        setupEditToolbar()
        handleIncomingData()
    }

    override fun voiceCommandHelp(): String {
        return "Try saying crop, rotate, filter, delete page, next page, previous page, AI summary, save PDF, or back."
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        val titleText = textAfterCommand(rawCommand, "title ", "set title ", "give title ", "name it ", "change title to ", "set title to ")

        return when {
            titleText.isNotEmpty() -> {
                customTitle = titleText
                binding.toolbar.title = titleText
                speak("Setting document title to $titleText")
                true
            }

            normalizedCommand.contains("crop") -> {
                speak("Cropping page.")
                toggleCrop()
                true
            }

            normalizedCommand.contains("rotate") -> {
                speak("Rotating page.")
                handleRotate()
                true
            }

            normalizedCommand.contains("filter") || normalizedCommand.contains("black") || normalizedCommand.contains("gray") -> {
                speak("Applying grayscale filter.")
                handleGrayScale()
                true
            }

            normalizedCommand.contains("delete") || normalizedCommand.contains("remove") -> {
                speak("Deleting current page.")
                handleDelete()
                true
            }

            normalizedCommand.contains("summary") || normalizedCommand.contains("ai") -> {
                speak("Opening AI summary.")
                binding.btnAiSummary.performClick()
                true
            }

            normalizedCommand.contains("save") || normalizedCommand.contains("pdf") -> {
                speak("Saving document as PDF.")
                generateAndSavePdf()
                true
            }

            normalizedCommand.contains("next page") || normalizedCommand == "next" -> {
                movePageBy(1)
                true
            }

            normalizedCommand.contains("previous page") || normalizedCommand.contains("back page") -> {
                movePageBy(-1)
                true
            }

            normalizedCommand.contains("page count") || normalizedCommand.contains("status") -> {
                speak("There are ${ScanSession.bitmaps.size} pages in the document.")
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

    private fun initViews() {
        adapter = PreviewPagerAdapter(ScanSession.bitmaps)
        binding.viewPager.adapter = adapter

        binding.btnSave.setOnClickListener { generateAndSavePdf() }

        binding.btnAiSummary.setOnClickListener {
            if (ScanSession.bitmaps.isEmpty()) {
                Toast.makeText(this, "No pages to summarize", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressBar.visibility = View.VISIBLE
            extractedText.setLength(0)

            lifecycleScope.launch(Dispatchers.Default) {
                var processedCount = 0
                ScanSession.bitmaps.forEach { bitmap ->
                    AiOcrUtils.extractText(bitmap, { text ->
                        extractedText.append(text).append("\n")
                        processedCount++
                        if (processedCount == ScanSession.bitmaps.size) navigateToSummary()
                    }, {
                        processedCount++
                        if (processedCount == ScanSession.bitmaps.size) navigateToSummary()
                    })
                }
            }
        }
    }

    private fun movePageBy(step: Int) {
        val nextIndex = (binding.viewPager.currentItem + step).coerceIn(0, (ScanSession.bitmaps.size - 1).coerceAtLeast(0))
        if (nextIndex == binding.viewPager.currentItem) {
            speak("No more pages in that direction.")
        } else {
            binding.viewPager.currentItem = nextIndex
            speak("Showing page ${nextIndex + 1}.")
        }
    }

    private fun navigateToSummary() {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.progressBar.visibility = View.GONE
            val intent = Intent(this@PreviewActivity, AiSummaryActivity::class.java)
            intent.putExtra("EXTRA_OCR_TEXT", extractedText.toString())
            startActivity(intent)
        }
    }

    private fun setupEditToolbar() {
        val tools = listOf(
            EditTool(1, "Crop", R.drawable.ic_crop),
            EditTool(2, "Rotate", R.drawable.ic_rotate),
            EditTool(3, "B/W", R.drawable.ic_filter),
            EditTool(4, "Delete", R.drawable.ic_delete)
        )

        binding.rvEditTools.apply {
            layoutManager = LinearLayoutManager(this@PreviewActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = EditToolsAdapter(tools) { tool ->
                when (tool.id) {
                    1 -> toggleCrop()
                    2 -> handleRotate()
                    3 -> handleGrayScale()
                    4 -> handleDelete()
                }
            }
        }
    }

    private fun toggleCrop() {
        if (binding.cropOverlay.visibility == View.VISIBLE) {
            handleCrop()
            binding.cropOverlay.visibility = View.GONE
        } else {
            binding.cropOverlay.visibility = View.VISIBLE
        }
    }

    private fun handleIncomingData() {
        val selectedDocUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("selected_doc_uri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("selected_doc_uri") as? Uri
        }

        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("selected_uris", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("selected_uris")
        }

        val importUris = buildList {
            if (!uris.isNullOrEmpty()) addAll(uris)
            if (selectedDocUri != null) add(selectedDocUri)
        }

        if (importUris.isEmpty()) return

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            ScanSession.clear()
            importUris.forEach { uri ->
                val mimeType = contentResolver.getType(uri) ?: ""
                try {
                    when {
                        mimeType == "application/pdf" -> renderPdfToBitmaps(uri)
                        mimeType.contains("word") || mimeType.contains("officedocument") || mimeType.contains("docx") -> {
                            renderWordToBitmaps(uri)
                        }
                        else -> {
                            contentResolver.openInputStream(uri)?.use { stream ->
                                val bitmap = BitmapFactory.decodeStream(stream)
                                bitmap?.let { ScanSession.bitmaps.add(it) }
                            }
                        }
                    }
                } catch (error: Exception) {
                    error.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
                binding.progressBar.visibility = View.GONE
                if (ScanSession.bitmaps.isNotEmpty()) binding.viewPager.currentItem = 0
            }
        }
    }

    private fun renderPdfToBitmaps(uri: Uri) {
        val pfd = contentResolver.openFileDescriptor(uri, "r") ?: return
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            ScanSession.bitmaps.add(bitmap)
            page.close()
        }
        renderer.close()
        pfd.close()
    }

    private fun renderWordToBitmaps(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            XWPFDocument(inputStream).use { doc ->
                val textBuilder = StringBuilder()
                doc.paragraphs.forEach { paragraph ->
                    textBuilder.append(paragraph.text).append("\n")
                }

                val fullText = textBuilder.toString()
                if (fullText.isEmpty()) return

                val fileName = FileUtils.getFileName(contentResolver, uri) ?: "Document"
                val bitmaps = com.hrishipvt.scantopdf.utils.NotePdfUtils.renderTextToBitmaps(fileName, fullText)
                ScanSession.bitmaps.addAll(bitmaps)
            }
        }
    }

    private fun handleCrop() {
        val index = binding.viewPager.currentItem
        if (index !in ScanSession.bitmaps.indices) return

        val original = ScanSession.bitmaps[index]
        val rect = binding.cropOverlay.getCropRect()
        val safeLeft = rect.left.toInt().coerceIn(0, original.width - 1)
        val safeTop = rect.top.toInt().coerceIn(0, original.height - 1)
        val safeWidth = rect.width().toInt().coerceIn(1, original.width - safeLeft)
        val safeHeight = rect.height().toInt().coerceIn(1, original.height - safeTop)
        try {
            val cropped = Bitmap.createBitmap(
                original,
                safeLeft,
                safeTop,
                safeWidth,
                safeHeight
            )
            ScanSession.bitmaps[index] = cropped
            adapter.notifyItemChanged(index)
            binding.cropOverlay.reset()
        } catch (error: Exception) {
            Toast.makeText(this, "Crop failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleRotate() {
        val index = binding.viewPager.currentItem
        if (index in ScanSession.bitmaps.indices) {
            ScanSession.bitmaps[index] = ImageUtils.rotate(ScanSession.bitmaps[index])
            adapter.notifyItemChanged(index)
        }
    }

    private fun handleGrayScale() {
        val index = binding.viewPager.currentItem
        if (index in ScanSession.bitmaps.indices) {
            ScanSession.bitmaps[index] = ImageUtils.toGray(ScanSession.bitmaps[index])
            adapter.notifyItemChanged(index)
        }
    }

    private fun handleDelete() {
        val index = binding.viewPager.currentItem
        if (ScanSession.bitmaps.size > 1) {
            ScanSession.bitmaps.removeAt(index)
            adapter.notifyItemRemoved(index)
            speak("Page deleted. ${ScanSession.bitmaps.size} pages remaining.")
        } else {
            Toast.makeText(this, "Cannot delete last page", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAndSavePdf() {
        if (ScanSession.bitmaps.isEmpty()) {
            Toast.makeText(this, "No pages available to save", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = android.graphics.pdf.PdfDocument()
                ScanSession.bitmaps.forEachIndexed { index, bitmap ->
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                }
                val saved = savePdfToIsolatedStorage(pdfDocument)
                pdfDocument.close()
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (saved) {
                        Toast.makeText(this@PreviewActivity, "PDF Saved to SmartScan Workspace!", Toast.LENGTH_SHORT).show()
                        ScanSession.clear()
                        finish()
                    } else {
                        Toast.makeText(this@PreviewActivity, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@PreviewActivity, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun savePdfToIsolatedStorage(pdfDocument: android.graphics.pdf.PdfDocument): Boolean {
        val safeTitle = customTitle?.replace(Regex("[^a-zA-Z0-9]"), "_") ?: "Scan"
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.pdf"
        
        return try {
            val isolatedDir = com.hrishipvt.scantopdf.utils.PdfUtils.getIsolatedPdfDirectory(this)
            val outputFile = File(isolatedDir, fileName)
            outputFile.outputStream().use {
                pdfDocument.writeTo(it)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
