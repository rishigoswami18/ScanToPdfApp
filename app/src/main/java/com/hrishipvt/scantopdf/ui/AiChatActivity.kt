package com.hrishipvt.scantopdf.ui

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.adapter.ChatAdapter
import com.hrishipvt.scantopdf.ai.GeminiApi
import com.hrishipvt.scantopdf.utils.FileUtils
import com.hrishipvt.scantopdf.view.ChatEntity
import com.hrishipvt.scantopdf.view.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import com.hrishipvt.scantopdf.db.AppDatabase

class AiChatActivity : AppCompatActivity() {

    private val messageList = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var progressBar: ProgressBar

    // Track the current file to be sent
    private var currentFileUri: Uri? = null

    private val takeCameraImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Retrieve the image URI sent back from CameraActivity
            val imageUri = result.data?.getParcelableExtra<Uri>("captured_image_uri")
            imageUri?.let {
                currentFileUri = it
                handleFileUpload(it)
            }
        }
    }

    // 1. Register Launchers
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            currentFileUri = it
            handleFileUpload(it)
        }
    }

    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            currentFileUri = it
            handleFileUpload(it)
        }
    }

    private val pickDriveFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            currentFileUri = it
            handleFileUpload(it)
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        val db = AppDatabase.getDatabase(this)
        val chatDao = db.chatDao()
        // --- NEW: BACK BUTTON LOGIC ---
        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.chatToolbar)
        setSupportActionBar(toolbar)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            // Or simply finish() if you just want to close the screen
        }


        lifecycleScope.launch(Dispatchers.IO) {
            val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            chatDao.deleteOldMessages(oneWeekAgo)

            // 2. Load remaining history into the UI
            chatDao.getAllMessages().collect { history ->
                withContext(Dispatchers.Main) {
                    messageList.clear()
                    messageList.addAll(history.map { ChatMessage(it.message, it.isUser) })
                    adapter.notifyDataSetChanged()
                    rvChat.scrollToPosition(messageList.size - 1)
                }
            }
        }

        // Initialize Views
        rvChat = findViewById(R.id.rvChatMessages)
        progressBar = findViewById(R.id.progressBar)

        val etMessage: EditText = findViewById(R.id.etMessage)
        val btnSend: MaterialButton = findViewById(R.id.btnSend)
        val btnPlus: ImageButton = findViewById(R.id.btnPlus)
        val actionContainer: LinearLayout = findViewById(R.id.actionContainer)

        adapter = ChatAdapter(messageList)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        if (messageList.isEmpty()) {
            addMessage("Hi! I'm your ScanToPdf Assistant, created by Hrishikesh Giri. How can I help you?", false)
        }

        btnPlus.setOnClickListener {
            if (actionContainer.visibility == View.GONE) {
                actionContainer.visibility = View.VISIBLE
                btnPlus.animate().rotation(45f).setDuration(200).start()
            } else {
                actionContainer.visibility = View.GONE
                btnPlus.animate().rotation(0f).setDuration(200).start()
            }
        }

        findViewById<ImageButton>(R.id.btnQuickPhoto).setOnClickListener {
            pickPhoto.launch("image/*")
            actionContainer.visibility = View.GONE
            btnPlus.rotation = 0f
        }

        findViewById<ImageButton>(R.id.btnQuickDrive).setOnClickListener {
            pickDriveFile.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            actionContainer.visibility = View.GONE
            btnPlus.rotation = 0f
        }
//        findViewById<ImageButton>(R.id.btnQuickDrive).setOnClickListener {
//            pickDriveFile.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
//
//        }


        findViewById<ImageButton>(R.id.btnQuickCamera).setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java).apply {
                putExtra("GET_IMAGE_ONLY", true)
            }
            takeCameraImage.launch(intent) // Fix: use launcher to get result back

            actionContainer.visibility = View.GONE
            btnPlus.rotation = 0f
        }

        btnSend.setOnClickListener {
            val query = etMessage.text.toString().trim()
            if (query.isNotEmpty() || currentFileUri != null) {
                val displayMsg = if (query.isEmpty()) "Analyzing attachment..." else query
                addMessage(displayMsg, true)

                progressBar.visibility = View.VISIBLE
                callGemini(query, currentFileUri)

                etMessage.text.clear()
                currentFileUri = null // Reset after sending
            }
        }
    }

    private fun handleFileUpload(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: ""

        // Fixes the "officedocument/wordprocessingml" typos and logic
        if (mimeType.contains("wordprocessingml.document")) {
            progressBar.visibility = View.VISIBLE

            lifecycleScope.launch(Dispatchers.IO) {
                val docText = FileUtils.getTextFromDocx(contentResolver, uri)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    // Use .ifEmpty {} to fix the warning
                    val message = docText.ifEmpty { "Analyzing Word Document..." }
                    addMessage("Word Doc Attached: ${uri.lastPathSegment}", true)
                    callGemini("Summarize this document: $message", null)
                }
            }
        } else {
            // Standard Image/PDF logic
            currentFileUri = uri
            addMessage("File attached: ${uri.lastPathSegment}", true)
        }
    }

    private fun callGemini(query: String, fileUri: Uri?) {
        // Corrected parameters to match GeminiApi.chatWithFile logic
        GeminiApi.chatWithFile(
            userMessage = if (query.isEmpty()) "Analyze this file" else query,
            fileUri = fileUri,
            contentResolver = contentResolver,
            onSuccess = { response: String ->
                progressBar.visibility = View.GONE
                addMessage(response, false)
            },
            onError = { error: String ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "AI Error: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun addMessage(text: String, isUser: Boolean) {
        val chatMessage = ChatMessage(text, isUser)
        messageList.add(chatMessage)
        adapter.notifyItemInserted(messageList.size - 1)

        // Save to Room
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(this@AiChatActivity).chatDao()
                .insertMessage(ChatEntity(message = text, isUser = isUser))
        }
    }

    fun getTextFromDocx(contentResolver: ContentResolver, uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val document = XWPFDocument(inputStream)
                val paragraphs = document.paragraphs
                val textBuilder = StringBuilder()
                for (paragraph in paragraphs) {
                    textBuilder.append(paragraph.text).append("\n")
                }
                textBuilder.toString()
            } ?: "Error: Could not open file"
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: Failed to extract text from Word document"
        }
    }
}