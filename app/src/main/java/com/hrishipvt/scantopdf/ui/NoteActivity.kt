package com.hrishipvt.scantopdf.ui

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.ai.GeminiApi
import com.hrishipvt.scantopdf.data.Note
import com.hrishipvt.scantopdf.databinding.ActivityNoteBinding
import com.hrishipvt.scantopdf.utils.FileUtils
import com.hrishipvt.scantopdf.utils.FirebaseNoteBackup
import com.hrishipvt.scantopdf.utils.NotePdfUtils
import com.hrishipvt.scantopdf.viewmodel.NoteViewModel
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class NoteActivity : VoiceEnabledActivity(), SensorEventListener {

    private lateinit var binding: ActivityNoteBinding
    private val viewModel: NoteViewModel by viewModels()
    private var noteId: Int = -1
    private var currentDialog: AlertDialog? = null
    private var isReadingAloud = false

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastAcceleration = 0f
    private var currentAcceleration = 0f
    private var acceleration = 0f
    private val shakeThreshold = 12f
    private var isShakeDialogShowing = false

    private val pickResume = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleResumeUpload(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVoiceAssistant()
        loadIntentData()
        setupListeners()
        setupSensors()
    }

    override fun voiceCommandHelp(): String {
        return "Try saying save note, share note, convert to PDF, read note, summarize note, title followed by text, write followed by text, or clear note."
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        val titleText = textAfterCommand(rawCommand, "title ", "set title ", "give title ", "change title to ", "set title to ")
        val bodyText = textAfterCommand(rawCommand, "write ", "dictate ", "append ", "add to note ", "add ", "note ", "text ")
        val replaceText = textAfterCommand(rawCommand, "replace note with ", "replace content with ", "set content to ")

        return when {
            titleText.isNotEmpty() -> {
                binding.etNoteTitle.setText(titleText)
                speak("Updated note title to $titleText")
                true
            }

            replaceText.isNotEmpty() -> {
                binding.etNoteContent.setText(replaceText)
                speak("Replaced note content.")
                true
            }

            bodyText.isNotEmpty() -> {
                appendToNote(bodyText)
                speak("Added $bodyText to your note.")
                true
            }

            normalizedCommand.contains("save") || normalizedCommand.contains("done") || normalizedCommand.contains("save this note") || normalizedCommand.contains("save note") -> {
                speak("Saving note.")
                saveNote()
                true
            }

            normalizedCommand.contains("share") || normalizedCommand.contains("send") || normalizedCommand.contains("share this note") || normalizedCommand.contains("share note") -> {
                speak("Opening share options.")
                shareNote()
                true
            }

            normalizedCommand.contains("pdf") || normalizedCommand.contains("convert") || normalizedCommand.contains("export") -> {
                speak("Converting note to PDF and saving.")
                convertToPdf()
                true
            }

            normalizedCommand.contains("backup") || normalizedCommand.contains("cloud") -> {
                speak("Backing up note.")
                backupToFirebase()
                true
            }

            normalizedCommand.contains("read") || normalizedCommand.contains("aloud") || normalizedCommand.contains("speak") -> {
                toggleReadAloud()
                true
            }

            normalizedCommand.contains("summarize") || normalizedCommand.contains("summary") -> {
                speak("Summarizing note.")
                processAiTask("Summarize this note briefly:", "Summarizing...")
                true
            }

            normalizedCommand.contains("grammar") || normalizedCommand.contains("fix") -> {
                speak("Fixing grammar.")
                processAiTask("Correct grammar and spelling in this note:", "Fixing grammar...")
                true
            }

            normalizedCommand.contains("professional tone") || normalizedCommand.contains("polish") -> {
                speak("Rewriting in a professional tone.")
                processAiTask("Rewrite this note in a professional business tone:", "Polishing...")
                true
            }

            normalizedCommand.contains("clear") || normalizedCommand.contains("delete text") -> {
                binding.etNoteContent.text.clear()
                speak("Cleared note content.")
                true
            }

            normalizedCommand.contains("analyze resume") || normalizedCommand.contains("upload resume") -> {
                speak("Select a resume file to analyze.")
                pickResume.launch("*/*")
                true
            }

            else -> false
        }
    }

    override fun onUnknownVoiceCommand(rawCommand: String, normalizedCommand: String) {
        if (rawCommand.length > 10 && !rawCommand.contains(" ")) {
            // Likely a single long word or gibberish, skip
            return
        }

        // Fallback: If it's a reasonably long sentence, just append it directly.
        // This makes dictation feel much more responsive.
        if (rawCommand.split(" ").size >= 3) {
            appendToNote(rawCommand)
            speak("Added to note.")
            return
        }

        speak("Processing your request.")
        binding.aiProgressBar.visibility = android.view.View.VISIBLE

        GeminiApi.processAiTask(
            prompt = "The user is in a note-taking app and said: '$rawCommand'. If this is a request to write something, provide that content. If it's a question, answer it briefly. Return ONLY the text to be added to the note.",
            content = binding.etNoteContent.text.toString(),
            onSuccess = { answer ->
                binding.aiProgressBar.visibility = android.view.View.GONE
                if (answer.isNotBlank() && !answer.contains("could not generate", ignoreCase = true)) {
                    appendToNote(answer.trim())
                    speak("Added.")
                } else {
                    // Final fallback: just add the raw command if AI is being difficult
                    appendToNote(rawCommand)
                    speak("Added exactly what you said.")
                }
            },
            onError = {
                binding.aiProgressBar.visibility = android.view.View.GONE
                // If AI fails (e.g. safety filter), just act as a simple dictation tool
                appendToNote(rawCommand)
                speak("Added to note.")
            }
        )
    }

    override fun onVoiceUtteranceCompleted(utteranceId: String?) {
        if (utteranceId == "READ_ALOUD") {
            runOnUiThread {
                isReadingAloud = false
                binding.btnReadAloud.setImageResource(R.drawable.ic_volume_up)
            }
        }
    }

    private fun appendToNote(text: String) {
        val currentText = binding.etNoteContent.text.toString().trim()
        val mergedText = if (currentText.isEmpty()) text else "$currentText\n\n$text"
        binding.etNoteContent.setText(mergedText.trim())
        binding.etNoteContent.setSelection(binding.etNoteContent.text?.length ?: 0)
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH
        acceleration = 0f
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        sensorManager?.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isShakeDialogShowing) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        lastAcceleration = currentAcceleration
        currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = currentAcceleration - lastAcceleration
        acceleration = acceleration * 0.9f + delta

        if (acceleration > shakeThreshold && binding.etNoteContent.text?.isNotEmpty() == true) {
            isShakeDialogShowing = true
            showShakeToClearDialog()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun showShakeToClearDialog() {
        currentDialog = AlertDialog.Builder(this)
            .setTitle("Shake Detected")
            .setMessage("Do you want to clear the current note text?")
            .setPositiveButton("Clear Text") { _, _ ->
                binding.etNoteContent.text.clear()
                isShakeDialogShowing = false
            }
            .setNegativeButton("Cancel") { _, _ ->
                isShakeDialogShowing = false
            }
            .setOnDismissListener {
                isShakeDialogShowing = false
            }
            .show()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.noteToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.noteToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun loadIntentData() {
        noteId = intent.getIntExtra("noteId", -1)
        val title = intent.getStringExtra("title")
        val content = intent.getStringExtra("content")

        if (noteId != -1) {
            binding.etNoteTitle.setText(title)
            binding.etNoteContent.setText(content)
            binding.noteToolbar.title = "Edit Note"
        } else {
            binding.noteToolbar.title = "New Note"
        }
    }

    private fun setupListeners() {
        binding.btnSaveNote.setOnClickListener { saveNote() }
        binding.btnShareNote.setOnClickListener { shareNote() }
        binding.btnConvertPdf.setOnClickListener { convertToPdf() }
        binding.btnBackup.setOnClickListener { backupToFirebase() }
        binding.btnAiEnhance.setOnClickListener { showAiEnhanceDialog() }
        binding.btnReadAloud.setOnClickListener { toggleReadAloud() }
    }

    private fun toggleReadAloud() {
        if (isReadingAloud) {
            stopVoiceSpeech()
            isReadingAloud = false
            binding.btnReadAloud.setImageResource(R.drawable.ic_volume_up)
            return
        }

        val content = binding.etNoteContent.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "Nothing to read!", Toast.LENGTH_SHORT).show()
            speak("The note is currently empty.")
            return
        }

        isReadingAloud = true
        binding.btnReadAloud.setImageResource(android.R.drawable.ic_media_pause)
        speak(content, "READ_ALOUD")
    }

    private fun showAiEnhanceDialog() {
        val options = arrayOf(
            "Summarize",
            "Fix Grammar",
            "Professional Tone",
            "Translate to Hindi",
            "Translate to Spanish",
            "Suggest Title",
            "Bullet Points",
            "ATS Resume Analysis (Upload)",
            "Enhance Current Text as Resume"
        )
        currentDialog = AlertDialog.Builder(this)
            .setTitle("AI Note Assistant")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> processAiTask("Summarize this note briefly:", "Summarizing...")
                    1 -> processAiTask("Correct grammar and spelling in this note:", "Fixing grammar...")
                    2 -> processAiTask("Rewrite this note in a professional business tone:", "Polishing...")
                    3 -> processAiTask("Translate this note to Hindi:", "Translating...")
                    4 -> processAiTask("Translate this note to Spanish:", "Translating...")
                    5 -> suggestAiTitle()
                    6 -> processAiTask("Convert this note into a clean bulleted list:", "Organizing...")
                    7 -> pickResume.launch("*/*")
                    8 -> showAtsJobDialog(binding.etNoteContent.text.toString())
                }
            }
            .show()
    }

    private fun handleResumeUpload(uri: Uri) {
        binding.aiProgressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val content = try {
                val mimeType = contentResolver.getType(uri)
                when {
                    mimeType?.contains("pdf") == true -> FileUtils.getTextFromPdf(contentResolver, uri)
                    mimeType?.contains("word") == true || mimeType?.contains("officedocument") == true -> FileUtils.getTextFromDocx(contentResolver, uri)
                    else -> FileUtils.readTextFromUri(contentResolver, uri)
                }
            } catch (error: Exception) {
                ""
            }

            withContext(Dispatchers.Main) {
                binding.aiProgressBar.visibility = android.view.View.GONE
                if (content.isNotEmpty()) {
                    binding.etNoteContent.setText(content)
                    showAtsJobDialog(content)
                } else {
                    Toast.makeText(this@NoteActivity, "Could not extract text from file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAtsJobDialog(resumeText: String) {
        if (resumeText.isEmpty()) {
            Toast.makeText(this, "Resume content is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val input = android.widget.EditText(this).apply {
            hint = "Paste Job Description here..."
        }
        currentDialog = AlertDialog.Builder(this)
            .setTitle("ATS Optimizer")
            .setMessage("Provide the Job Description to analyze and enhance your resume.")
            .setView(input)
            .setPositiveButton("Analyze & Enhance") { _, _ ->
                val jd = input.text.toString().trim()
                if (jd.isNotEmpty()) {
                    val prompt = """
                        Act as an expert ATS optimizer and professional resume writer.
                        
                        1. Provide a Match Score (0-100%) for the current resume against the job description.
                        2. Identify missing critical keywords and skills.
                        3. Generate an enhanced version of the resume that remains truthful but is optimized for the job description.
                        
                        Job Description: $jd
                        
                        Current Resume: $resumeText
                        
                        Format your response as:
                        MATCH SCORE: [Score]%
                        
                        MISSING KEYWORDS:
                        - [Keyword 1]
                        
                        ENHANCED RESUME:
                        [Full enhanced resume text here]
                    """.trimIndent()
                    processAiTask(prompt, "Generating ATS optimized resume...")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun suggestAiTitle() {
        val content = binding.etNoteContent.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "Note is empty", Toast.LENGTH_SHORT).show()
            return
        }

        binding.aiProgressBar.visibility = android.view.View.VISIBLE
        GeminiApi.processAiTask(
            "Suggest a short, 3-5 word professional title for this note content. Return only the title:",
            content,
            onSuccess = { result ->
                binding.aiProgressBar.visibility = android.view.View.GONE
                binding.etNoteTitle.setText(result.removeSurrounding("\""))
                Toast.makeText(this, "Title suggested!", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                binding.aiProgressBar.visibility = android.view.View.GONE
                Toast.makeText(this, "AI Error: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun processAiTask(prompt: String, loadingMsg: String) {
        val content = binding.etNoteContent.text.toString().trim()
        if (content.isEmpty() && !prompt.contains("Resume")) {
            Toast.makeText(this, "Content is empty", Toast.LENGTH_SHORT).show()
            return
        }

        binding.aiProgressBar.visibility = android.view.View.VISIBLE
        Toast.makeText(this, loadingMsg, Toast.LENGTH_SHORT).show()

        GeminiApi.processAiTask(
            prompt,
            content,
            onSuccess = { result ->
                binding.aiProgressBar.visibility = android.view.View.GONE
                showAiResultDialog(result)
            },
            onError = { error ->
                binding.aiProgressBar.visibility = android.view.View.GONE
                Toast.makeText(this, "AI Error: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showAiResultDialog(result: String) {
        currentDialog = AlertDialog.Builder(this)
            .setTitle("AI Insights & Enhanced Content")
            .setMessage(result)
            .setPositiveButton("Apply Enhanced Text") { _, _ ->
                val enhancedPart = if (result.contains("ENHANCED RESUME:")) {
                    result.substringAfter("ENHANCED RESUME:").trim()
                } else {
                    result
                }
                binding.etNoteContent.setText(enhancedPart)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun saveNote() {
        val title = binding.etNoteTitle.text.toString().trim()
        val content = binding.etNoteContent.text.toString().trim()

        if (content.isEmpty()) {
            Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "local"

        val note = Note(
            id = if (noteId == -1) 0 else noteId,
            title = if (title.isEmpty()) "Untitled" else title,
            content = content,
            time = System.currentTimeMillis(),
            userId = userId
        )

        viewModel.saveNote(note)
        Toast.makeText(this, "Note Saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun shareNote() {
        val title = binding.etNoteTitle.text.toString().trim()
        val content = binding.etNoteContent.text.toString().trim()
        if (content.isEmpty()) return

        val shareText = if (title.isNotEmpty()) "$title\n\n$content" else content
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Share note"))
    }

    private fun convertToPdf() {
        val title = binding.etNoteTitle.text.toString().trim()
        val content = binding.etNoteContent.text.toString().trim()
        if (content.isEmpty()) return

        val pdfFile = NotePdfUtils.createPdf(this, if (title.isEmpty()) "Note" else title, content)
        if (pdfFile != null) {
            Toast.makeText(this, "PDF Saved: ${pdfFile.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to create PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun backupToFirebase() {
        val title = binding.etNoteTitle.text.toString().trim()
        val content = binding.etNoteContent.text.toString().trim()
        if (content.isEmpty()) return

        val note = Note(title = title, content = content)
        FirebaseNoteBackup.backup(note)
        Toast.makeText(this, "Backup Successful", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        currentDialog?.dismiss()
        super.onDestroy()
    }
}
