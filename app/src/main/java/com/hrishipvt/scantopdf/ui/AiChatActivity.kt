package com.hrishipvt.scantopdf.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.adapter.ChatAdapter
import com.hrishipvt.scantopdf.ai.GeminiApi
import com.hrishipvt.scantopdf.databinding.ActivityAiChatBinding
import com.hrishipvt.scantopdf.db.AppDatabase
import com.hrishipvt.scantopdf.utils.FileUtils
import com.hrishipvt.scantopdf.utils.NotePdfUtils
import com.hrishipvt.scantopdf.view.ChatEntity
import com.hrishipvt.scantopdf.view.ChatMessage
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiChatActivity : VoiceEnabledActivity() {

    companion object {
        const val EXTRA_STARTER_PROMPT = "extra_starter_prompt"
        const val EXTRA_AUTO_SEND = "extra_auto_send"
    }

    private lateinit var binding: ActivityAiChatBinding
    private val messageList = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private var currentFileUri: Uri? = null
    private var shouldSpeakNextAiReply = false

    private val takeCameraImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra("captured_image_uri", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra("captured_image_uri")
            }
            imageUri?.let {
                currentFileUri = it
                handleFileUpload(it)
            }
        }
    }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
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
        binding = ActivityAiChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVoiceAssistant()
        setupRecyclerView()
        setupListeners()
        setupQuickPrompts()
        loadChatHistory()
        if (savedInstanceState == null) {
            handleIncomingIntent()
            handleStarterPrompt()
        }
    }

    override fun voiceCommandHelp(): String {
        return "Try saying message followed by your question, attach photo, attach document, open camera, send, read last reply, clear chat, or export chat."
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        val dictatedPrompt = textAfterCommand(rawCommand, "message ", "ask ", "question ", "send ")

        return when {
            dictatedPrompt.isNotEmpty() -> {
                binding.etMessage.setText(dictatedPrompt)
                binding.etMessage.setSelection(binding.etMessage.text?.length ?: 0)
                sendCurrentMessage(readReply = true)
                true
            }

            normalizedCommand.contains("attach photo") || normalizedCommand.contains("upload photo") || normalizedCommand.contains("pick photo") || normalizedCommand.contains("gallery") -> {
                speak("Choose a photo to analyze.")
                binding.btnQuickPhoto.performClick()
                true
            }

            normalizedCommand.contains("attach file") || normalizedCommand.contains("attach document") || normalizedCommand.contains("attach pdf") || normalizedCommand.contains("open drive") || normalizedCommand.contains("upload document") -> {
                speak("Choose a document to analyze.")
                binding.btnQuickDrive.performClick()
                true
            }

            normalizedCommand.contains("camera") || normalizedCommand.contains("capture") || normalizedCommand.contains("take photo") -> {
                speak("Opening camera capture.")
                binding.btnQuickCamera.performClick()
                true
            }

            normalizedCommand.contains("read last reply") || normalizedCommand.contains("read response") || normalizedCommand.contains("read answer") -> {
                val reply = messageList.lastOrNull { !it.isUser }?.text?.trim().orEmpty()
                if (reply.isNotEmpty()) {
                    speak(reply.take(320))
                } else {
                    speak("There is no AI response to read yet.")
                }
                true
            }

            normalizedCommand.contains("clear chat") || normalizedCommand.contains("delete chat") -> {
                speak("Clearing the chat.")
                clearChatHistory()
                true
            }

            normalizedCommand.contains("export chat") || normalizedCommand.contains("save chat") || normalizedCommand.contains("download chat") -> {
                speak("Exporting this conversation to PDF.")
                exportChatToPdf()
                true
            }

            normalizedCommand.contains("status") || normalizedCommand.contains("attachment status") -> {
                val attachmentName = currentFileUri?.lastPathSegment ?: "No file attached"
                speak("This chat has ${messageList.size} messages. Current attachment: $attachmentName.")
                true
            }

            normalizedCommand.contains("send") || normalizedCommand.contains("run ai") || normalizedCommand.contains("analyze attachment") -> {
                sendCurrentMessage(readReply = true)
                true
            }

            else -> false
        }
    }

    override fun onUnknownVoiceCommand(rawCommand: String, normalizedCommand: String) {
        if (binding.progressBar.visibility == View.VISIBLE) {
            speak("I am still processing the previous request.")
            return
        }

        binding.etMessage.setText(rawCommand)
        binding.etMessage.setSelection(binding.etMessage.text?.length ?: 0)
        sendCurrentMessage(readReply = true)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.chatToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.chatToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_pdf -> {
                exportChatToPdf()
                true
            }

            R.id.action_clear_chat -> {
                clearChatHistory()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportChatToPdf() {
        if (messageList.isEmpty()) {
            Toast.makeText(this, "Chat is empty", Toast.LENGTH_SHORT).show()
            speak("There is no chat history to export.")
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val chatContent = StringBuilder()
            messageList.forEach { msg ->
                val sender = if (msg.isUser) "You" else "AI Assistant"
                chatContent.append("$sender:\n${msg.text}\n\n")
            }

            val pdfFile = NotePdfUtils.createPdf(
                context = this@AiChatActivity,
                title = "AI Chat Export",
                content = chatContent.toString()
            )

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                if (pdfFile != null) {
                    Toast.makeText(this@AiChatActivity, "Chat exported to: ${pdfFile.name}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@AiChatActivity, "Failed to export chat", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun clearChatHistory() {
        AlertDialog.Builder(this)
            .setTitle("Clear Chat")
            .setMessage("Are you sure you want to delete all chat history?")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@AiChatActivity).chatDao().clearAll()
                    withContext(Dispatchers.Main) {
                        messageList.clear()
                        adapter.notifyDataSetChanged()
                        addMessage("Chat cleared. How can I help you today?", false)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(messageList)
        binding.rvChatMessages.apply {
            layoutManager = LinearLayoutManager(this@AiChatActivity)
            adapter = this@AiChatActivity.adapter
        }
    }

    private fun handleIncomingIntent() {
        val pdfUriFromList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("pdf_uri_from_list", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("pdf_uri_from_list") as? Uri
        }

        pdfUriFromList?.let {
            currentFileUri = it
            addMessage("Started AI Chat with PDF: ${it.lastPathSegment}", true)
            binding.progressBar.visibility = View.VISIBLE
            callGemini("Please provide a professional summary and key points of this PDF document.", it)
        }
    }

    private fun setupListeners() {
        binding.btnPlus.setOnClickListener {
            if (binding.actionContainer.visibility == View.GONE) {
                binding.actionContainer.visibility = View.VISIBLE
                binding.btnPlus.animate().rotation(45f).setDuration(200).start()
            } else {
                binding.actionContainer.visibility = View.GONE
                binding.btnPlus.animate().rotation(0f).setDuration(200).start()
            }
        }

        binding.btnQuickPhoto.setOnClickListener {
            pickPhoto.launch("image/*")
            hideActionContainer()
        }

        binding.btnQuickDrive.setOnClickListener {
            pickDriveFile.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            hideActionContainer()
        }

        binding.btnQuickCamera.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java).apply {
                putExtra("GET_IMAGE_ONLY", true)
            }
            takeCameraImage.launch(intent)
            hideActionContainer()
        }

        binding.btnSend.setOnClickListener {
            sendCurrentMessage(readReply = false)
        }
    }

    private fun setupQuickPrompts() {
        binding.chipPromptSummary.setOnClickListener {
            sendSuggestedPrompt(getString(R.string.ai_prompt_summary_request))
        }

        binding.chipPromptPitch.setOnClickListener {
            sendSuggestedPrompt(getString(R.string.ai_prompt_pitch_request))
        }

        binding.chipPromptExplain.setOnClickListener {
            sendSuggestedPrompt(getString(R.string.ai_prompt_explain_request))
        }

        binding.chipPromptActions.setOnClickListener {
            sendSuggestedPrompt(getString(R.string.ai_prompt_actions_request))
        }
    }

    private fun hideActionContainer() {
        binding.actionContainer.visibility = View.GONE
        binding.btnPlus.rotation = 0f
    }

    private fun loadChatHistory() {
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                db.chatDao().deleteOldMessages(oneWeekAgo)
            }

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.chatDao().getAllMessages().collect { history ->
                    messageList.clear()
                    messageList.addAll(history.map { ChatMessage(it.message, it.isUser) })
                    if (messageList.isEmpty()) {
                        addMessage("Hi! I'm your ScanToPdf AI Assistant. I can summarize PDFs, extract text, and answer questions about your documents. How can I help?", false)
                    } else {
                        adapter.notifyDataSetChanged()
                        binding.rvChatMessages.scrollToPosition(messageList.size - 1)
                    }
                }
            }
        }
    }

    private fun handleStarterPrompt() {
        val starterPrompt = intent.getStringExtra(EXTRA_STARTER_PROMPT)?.trim().orEmpty()
        if (starterPrompt.isBlank()) return

        val autoSend = intent.getBooleanExtra(EXTRA_AUTO_SEND, false)
        binding.etMessage.setText(starterPrompt)
        binding.etMessage.setSelection(binding.etMessage.text?.length ?: 0)
        intent.removeExtra(EXTRA_STARTER_PROMPT)
        intent.removeExtra(EXTRA_AUTO_SEND)

        if (autoSend) {
            sendCurrentMessage(readReply = true)
        }
    }

    private fun handleFileUpload(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: ""
        if (mimeType.contains("wordprocessingml.document")) {
            binding.progressBar.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                val docText = FileUtils.getTextFromDocx(contentResolver, uri)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    val message = docText.ifEmpty { "Analyzing Word Document..." }
                    addMessage("Word Doc Attached: ${uri.lastPathSegment}", true)
                    callGemini("Summarize this document: $message", null)
                }
            }
        } else {
            currentFileUri = uri
            addMessage("File attached: ${uri.lastPathSegment}", true)
        }
    }

    private fun sendCurrentMessage(readReply: Boolean) {
        val query = binding.etMessage.text.toString().trim()
        if (query.isEmpty() && currentFileUri == null) {
            Toast.makeText(this, "Type a message or attach a file first", Toast.LENGTH_SHORT).show()
            speak("Please say message followed by your request, or attach a file first.")
            return
        }

        shouldSpeakNextAiReply = readReply
        val displayMsg = if (query.isEmpty()) "Analyzing attachment..." else query
        addMessage(displayMsg, true)

        binding.progressBar.visibility = View.VISIBLE
        callGemini(query, currentFileUri)
        binding.etMessage.text?.clear()
    }

    private fun sendSuggestedPrompt(prompt: String) {
        binding.etMessage.setText(prompt)
        binding.etMessage.setSelection(binding.etMessage.text?.length ?: 0)
        sendCurrentMessage(readReply = true)
    }

    private fun callGemini(query: String, fileUri: Uri?) {
        GeminiApi.chatWithFile(
            userMessage = if (query.isEmpty()) "Analyze this file" else query,
            fileUri = fileUri,
            contentResolver = contentResolver,
            onSuccess = { response ->
                binding.progressBar.visibility = View.GONE
                addMessage(response, false)
                if (shouldSpeakNextAiReply) {
                    shouldSpeakNextAiReply = false
                    speak(response.take(320))
                }
            },
            onError = { error ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "AI Error: $error", Toast.LENGTH_SHORT).show()
                if (shouldSpeakNextAiReply) {
                    shouldSpeakNextAiReply = false
                    speak("I could not complete that AI request.")
                }
            }
        )
    }

    private fun addMessage(text: String, isUser: Boolean) {
        val chatMessage = ChatMessage(text, isUser)
        messageList.add(chatMessage)
        adapter.notifyItemInserted(messageList.size - 1)
        binding.rvChatMessages.smoothScrollToPosition(messageList.size - 1)

        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(this@AiChatActivity).chatDao()
                .insertMessage(ChatEntity(message = text, isUser = isUser))
        }
    }
}
