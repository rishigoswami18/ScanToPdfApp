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
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.adapter.EditToolsAdapter
import com.hrishipvt.scantopdf.adapter.PreviewPagerAdapter
import com.hrishipvt.scantopdf.ai.AiOcrUtils
import com.hrishipvt.scantopdf.view.EditTool
import com.hrishipvt.scantopdf.utils.ImageUtils
import com.hrishipvt.scantopdf.utils.ScanSession
import com.hrishipvt.scantopdf.view.CropOverlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class PreviewActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var adapter: PreviewPagerAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var rvEditTools: RecyclerView
    private var extractedText = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        initViews()
        setupEditToolbar()
        handleIncomingData()
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        cropOverlay = findViewById(R.id.cropOverlay)
        progressBar = findViewById(R.id.progressBar)
        rvEditTools = findViewById(R.id.rvEditTools)

        adapter = PreviewPagerAdapter(ScanSession.bitmaps)
        viewPager.adapter = adapter

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { generateAndSavePdf() }

        // Final AI Summary Logic: Extract text from ALL pages before navigating
        findViewById<MaterialButton>(R.id.btnAiSummary).setOnClickListener {
            if (ScanSession.bitmaps.isEmpty()) {
                Toast.makeText(this, "No pages to summarize", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            extractedText.setLength(0) // Clear previous OCR session

            lifecycleScope.launch(Dispatchers.Default) {
                var processedCount = 0
                ScanSession.bitmaps.forEach { bitmap ->
                    AiOcrUtils.extractText(bitmap, { text ->
                        extractedText.append(text).append("\n")
                        processedCount++

                        // Check if all pages are done
                        if (processedCount == ScanSession.bitmaps.size) {
                            navigateToSummary()
                        }
                    }, {
                        processedCount++
                        if (processedCount == ScanSession.bitmaps.size) navigateToSummary()
                    })
                }
            }
        }
    }

    private fun navigateToSummary() {
        lifecycleScope.launch(Dispatchers.Main) {
            progressBar.visibility = View.GONE
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
            EditTool(4, "Delete", R.drawable.ic_delete),
            EditTool(5, "OCR", R.drawable.ic_word_to_pdf)
        )

        rvEditTools.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvEditTools.adapter = EditToolsAdapter(tools) { tool ->
            when (tool.id) {
                1 -> handleCrop()
                2 -> handleRotate()
                3 -> handleGrayScale()
                4 -> handleDelete()
                5 -> Toast.makeText(this, "Auto-OCR enabled for Summary", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleIncomingData() {

        progressBar.visibility = View.VISIBLE

        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("selected_uris", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("selected_uris")
        }

        if (uris.isNullOrEmpty()) return

        progressBar.visibility = View.VISIBLE
        // ScanSession.bitmaps.clear() // Uncomment if you want to replace current session

        lifecycleScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                val mimeType = contentResolver.getType(uri) ?: ""
                try {
                    when {
                        mimeType == "application/pdf" -> renderPdfToBitmaps(uri)
                        mimeType.contains("word") || mimeType.contains("officedocument") -> renderWordToBitmaps(uri)
                        else -> {
                            contentResolver.openInputStream(uri)?.use { stream ->
                                val bitmap = BitmapFactory.decodeStream(stream)
                                bitmap?.let { ScanSession.bitmaps.add(it) }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            withContext(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
                if (ScanSession.bitmaps.isNotEmpty()) viewPager.currentItem = 0
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
            val doc = org.apache.poi.xwpf.usermodel.XWPFDocument(inputStream)
            val text = org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc).text

            val bitmap = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 32f }

            var y = 80f
            text.split("\n").take(40).forEach { line ->
                canvas.drawText(if (line.length > 80) line.take(77) + "..." else line, 50f, y, paint)
                y += 45f
            }
            ScanSession.bitmaps.add(bitmap)
            doc.close()
        }
    }

    private fun handleCrop() {
        val index = viewPager.currentItem
        if (index !in ScanSession.bitmaps.indices) return
        val original = ScanSession.bitmaps[index]
        val rect = cropOverlay.getCropRect()
        try {
            val cropped = Bitmap.createBitmap(original,
                rect.left.toInt().coerceAtLeast(0), rect.top.toInt().coerceAtLeast(0),
                rect.width().toInt().coerceAtMost(original.width), rect.height().toInt().coerceAtMost(original.height))
            ScanSession.bitmaps[index] = cropped
            adapter.notifyItemChanged(index)
            cropOverlay.reset()
        } catch (e: Exception) { Toast.makeText(this, "Crop failed", Toast.LENGTH_SHORT).show() }
    }

    private fun handleRotate() {
        val i = viewPager.currentItem
        if (i in ScanSession.bitmaps.indices) {
            ScanSession.bitmaps[i] = ImageUtils.rotate(ScanSession.bitmaps[i])
            adapter.notifyItemChanged(i)
        }
    }

    private fun handleGrayScale() {
        val i = viewPager.currentItem
        if (i in ScanSession.bitmaps.indices) {
            ScanSession.bitmaps[i] = ImageUtils.toGray(ScanSession.bitmaps[i])
            adapter.notifyItemChanged(i)
        }
    }

    private fun handleDelete() {
        val i = viewPager.currentItem
        if (ScanSession.bitmaps.size > 1) {
            ScanSession.bitmaps.removeAt(i)
            adapter.notifyItemRemoved(i)
        }
    }

    private fun generateAndSavePdf() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = android.graphics.pdf.PdfDocument()
                ScanSession.bitmaps.forEachIndexed { index, bitmap ->
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                }
                savePdfToDownloads(pdfDocument)
                pdfDocument.close()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@PreviewActivity, "PDF Saved to Downloads!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { progressBar.visibility = View.GONE }
            }
        }
    }

    private fun savePdfToDownloads(pdfDocument: android.graphics.pdf.PdfDocument) {
        val fileName = "Scan_${System.currentTimeMillis()}.pdf"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { pdfDocument.writeTo(it) }
        }
    }
}