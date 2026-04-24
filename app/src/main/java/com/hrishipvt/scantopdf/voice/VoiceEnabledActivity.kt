package com.hrishipvt.scantopdf.voice

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.ai.GeminiApi
import com.hrishipvt.scantopdf.ui.AiChatActivity
import com.hrishipvt.scantopdf.ui.AiSummaryActivity
import com.hrishipvt.scantopdf.ui.CameraActivity
import com.hrishipvt.scantopdf.ui.LoginActivity
import com.hrishipvt.scantopdf.ui.MainActivity
import com.hrishipvt.scantopdf.ui.MergePdfActivity
import com.hrishipvt.scantopdf.ui.NoteActivity
import com.hrishipvt.scantopdf.ui.NotesListActivity
import com.hrishipvt.scantopdf.ui.PdfListActivity
import com.hrishipvt.scantopdf.ui.SignupActivity
import java.util.Locale

abstract class VoiceEnabledActivity : AppCompatActivity() {

    companion object {
        const val APP_CONFIG_PREFS = "AppConfig"
        const val KEY_AUTONOMOUS_VOICE = "AUTONOMOUS_VOICE"
        const val KEY_VOICE_PERMISSION_ASKED = "VOICE_PERMISSION_ASKED"
        const val EXTRA_OPEN_SETTINGS = "OPEN_SETTINGS"
        const val KEY_APP_MODE = "APP_MODE"
        const val KEY_MODE_CHOSEN = "MODE_CHOSEN"
        const val MODE_VOICE = "voice"
        const val MODE_MANUAL = "manual"

        fun normalizeSpokenEmail(input: String): String {
            return input.lowercase(Locale.getDefault())
                .replace(" at the rate ", "@")
                .replace(" at ", "@")
                .replace(" dot ", ".")
                .replace(" underscore ", "_")
                .replace(" dash ", "-")
                .replace(" hyphen ", "-")
                .replace(" space ", "")
                .replace(" ", "")
        }

        fun textAfterCommand(rawCommand: String, vararg prefixes: String): String {
            val trimmed = rawCommand.trim()
            val lower = trimmed.lowercase(Locale.getDefault())
            prefixes.forEach { prefix ->
                val normalizedPrefix = prefix.lowercase(Locale.getDefault())
                if (lower.startsWith(normalizedPrefix)) {
                    return trimmed.substring(prefix.length).trim()
                }
            }
            return ""
        }
    }

    private val speechHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var existingVoiceButton: View? = null
    private var isTtsSpeaking = false
    private var isVoiceActiveForScreen = false

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableVoiceAssistant(announce = true)
                onVoicePermissionResult(true)
            } else {
                Toast.makeText(this, "Microphone permission is required for voice control.", Toast.LENGTH_SHORT).show()
                updateVoiceButtonState()
                onVoicePermissionResult(false)
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, "Notifications enabled.", Toast.LENGTH_SHORT).show()
            }
        }

    protected val appConfig by lazy { getSharedPreferences(APP_CONFIG_PREFS, MODE_PRIVATE) }

    protected open fun defaultAutonomousVoiceEnabled(): Boolean = true

    protected open fun voiceButtonBottomMarginDp(): Int = 28

    protected open fun voiceCommandHelp(): String {
        return "Try saying home, files, notes, scan, AI assistant, merge, settings, back, or stop listening."
    }

    protected open fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean = false

    protected open fun onUnknownVoiceCommand(rawCommand: String, normalizedCommand: String) {
        speak("Let me think about that.")
        GeminiApi.processAiTask(
            prompt = "You are a smart autonomous voice assistant inside a mobile document productivity app called SmartScan. The user said: '$rawCommand'. If it's a question about the app or documents, answer it. Otherwise, respond briefly in one or two short sentences.",
            content = "Current screen: ${this.javaClass.simpleName}",
            onSuccess = { answer -> speak(answer.replace("*", "")) },
            onError = { speak("I did not catch that. ${voiceCommandHelp()}") }
        )
    }

    protected open fun onVoicePermissionResult(granted: Boolean) = Unit

    protected open fun openSettingsFromVoice(): Boolean = false

    protected open fun onVoiceUtteranceCompleted(utteranceId: String?) = Unit

    protected fun isVoiceMode(): Boolean {
        return appConfig.getString(KEY_APP_MODE, MODE_VOICE) == MODE_VOICE
    }

    protected fun setAppMode(mode: String) {
        appConfig.edit()
            .putString(KEY_APP_MODE, mode)
            .putBoolean(KEY_MODE_CHOSEN, true)
            .apply()

        if (mode == MODE_MANUAL) {
            isVoiceActiveForScreen = false
            stopListeningInternal()
            existingVoiceButton?.visibility = View.GONE
            appConfig.edit().putBoolean(KEY_AUTONOMOUS_VOICE, false).apply()
        } else {
            existingVoiceButton?.visibility = View.VISIBLE
            updateVoiceButtonState()
        }
    }

    protected fun isModeChosen(): Boolean {
        return appConfig.getBoolean(KEY_MODE_CHOSEN, false)
    }

    protected fun setupVoiceAssistant() {
        if (!isVoiceMode()) {
            // Manual mode — no voice features, hide FAB
            existingVoiceButton?.visibility = View.GONE
            maybeRequestNotificationPermission()
            return
        }

        ensureVoiceButton()
        initializeTextToSpeech()
        if (isVoiceRecognitionSupported()) {
            initializeSpeechRecognizer()
            maybeRequestMicrophoneForAutonomousMode()
        }
        updateVoiceButtonState()
        maybeRequestNotificationPermission()
    }

    protected fun showModeChooserDialog(onDismiss: (() -> Unit)? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_mode_chooser, null)
        val radioVoice = dialogView.findViewById<android.widget.RadioButton>(R.id.radioVoice)
        val radioManual = dialogView.findViewById<android.widget.RadioButton>(R.id.radioManual)
        val cardVoice = dialogView.findViewById<android.widget.LinearLayout>(R.id.cardVoiceMode)
        val cardManual = dialogView.findViewById<android.widget.LinearLayout>(R.id.cardManualMode)

        // Default to voice mode
        radioVoice.isChecked = isVoiceMode()
        radioManual.isChecked = !isVoiceMode()

        cardVoice.setOnClickListener {
            radioVoice.isChecked = true
            radioManual.isChecked = false
        }
        cardManual.setOnClickListener {
            radioManual.isChecked = true
            radioVoice.isChecked = false
        }
        radioVoice.setOnCheckedChangeListener { _, checked ->
            if (checked) radioManual.isChecked = false
        }
        radioManual.setOnCheckedChangeListener { _, checked ->
            if (checked) radioVoice.isChecked = false
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Choose Your Mode")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Continue") { _, _ ->
                val mode = if (radioVoice.isChecked) MODE_VOICE else MODE_MANUAL
                setAppMode(mode)
                if (mode == MODE_VOICE) {
                    setupVoiceAssistant()
                }
                onDismiss?.invoke()
            }
            .show()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    protected fun speak(text: String, utteranceId: String = "VOICE_REPLY") {
        val tts = textToSpeech ?: return
        // Stop listening BEFORE speaking to prevent echo feedback
        stopListeningInternal()
        isTtsSpeaking = true
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    protected fun stopVoiceSpeech() {
        textToSpeech?.stop()
        isTtsSpeaking = false
        if (shouldKeepVoiceActive() && hasAudioPermission()) {
            startListeningInternal()
        }
    }

    protected fun setAutonomousVoiceEnabled(enabled: Boolean) {
        appConfig.edit().putBoolean(KEY_AUTONOMOUS_VOICE, enabled).apply()
        if (enabled) {
            enableVoiceAssistant(announce = true)
        } else if (!isVoiceActiveForScreen) {
            stopListeningInternal()
            updateVoiceButtonState()
        }
    }

    protected fun isAutonomousVoiceEnabled(): Boolean {
        return appConfig.getBoolean(KEY_AUTONOMOUS_VOICE, defaultAutonomousVoiceEnabled())
    }

    private fun maybeRequestMicrophoneForAutonomousMode() {
        if (!isAutonomousVoiceEnabled() || hasAudioPermission()) return
        if (appConfig.getBoolean(KEY_VOICE_PERMISSION_ASKED, false)) return

        appConfig.edit().putBoolean(KEY_VOICE_PERMISSION_ASKED, true).apply()
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun ensureVoiceButton() {
        existingVoiceButton = findViewById(R.id.fabVoiceCommand)
        if (existingVoiceButton != null) {
            existingVoiceButton?.setOnClickListener { toggleVoiceAssistant() }
            return
        }

        val contentRoot = findViewById<FrameLayout>(android.R.id.content)
        val fab = FloatingActionButton(this).apply {
            setImageResource(R.drawable.ic_mic)
            contentDescription = getString(R.string.voice_command)
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary_warm))
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface))
            setOnClickListener { toggleVoiceAssistant() }
        }

        val margin = dpToPx(voiceButtonBottomMarginDp())
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply {
            setMargins(margin, margin, margin, margin)
        }

        contentRoot.addView(fab, params)
        existingVoiceButton = fab
    }

    private fun initializeTextToSpeech() {
        if (textToSpeech != null) return

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        isTtsSpeaking = false
                        onVoiceUtteranceCompleted(utteranceId)
                        if (shouldKeepVoiceActive() && hasAudioPermission()) {
                            // Delay resume to avoid picking up tail-end of TTS audio
                            speechHandler.postDelayed({ startListeningInternal() }, 600)
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        isTtsSpeaking = false
                        onVoiceUtteranceCompleted(utteranceId)
                        if (shouldKeepVoiceActive() && hasAudioPermission()) {
                            speechHandler.postDelayed({ startListeningInternal() }, 600)
                        }
                    }
                })
            }
        }
    }

    private fun initializeSpeechRecognizer() {
        if (speechRecognizer != null || !isVoiceRecognitionSupported()) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    if (shouldKeepVoiceActive() && hasAudioPermission()) {
                        speechHandler.postDelayed({ startListeningInternal() }, 500)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val rawCommand = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (rawCommand.isBlank()) {
                        if (shouldKeepVoiceActive() && hasAudioPermission()) {
                            speechHandler.postDelayed({ startListeningInternal() }, 500)
                        }
                        return
                    }

                    handleVoiceCommand(rawCommand)

                    if (!isTtsSpeaking && shouldKeepVoiceActive() && hasAudioPermission()) {
                        speechHandler.postDelayed({ startListeningInternal() }, 450)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun handleVoiceCommand(rawCommand: String) {
        val normalizedCommand = rawCommand.lowercase(Locale.getDefault())
        if (handleScreenVoiceCommand(rawCommand, normalizedCommand)) return
        if (handleGlobalVoiceCommand(normalizedCommand)) return
        onUnknownVoiceCommand(rawCommand, normalizedCommand)
    }

    private fun handleGlobalVoiceCommand(command: String): Boolean {
        // Support for "Open X", "Go to X", "Show me X" and Hindi "X dikhao"
        val targetScreen = textAfterCommand(command, "open ", "go to ", "show me ", "dikhao ", "khola ")
        val navigationRequest = targetScreen.ifEmpty { command }

        return when {
            containsAny(command, "stop listening", "voice off", "disable voice", "mute voice", "band karo") -> {
                isVoiceActiveForScreen = false
                stopListeningInternal()
                updateVoiceButtonState()
                speak("Voice assistant disabled.")
                true
            }

            containsAny(navigationRequest, "home", "dashboard", "main", "ghar") -> {
                speak("Opening home.")
                launchActivity(MainActivity::class.java, clearTop = true)
                true
            }

            containsAny(navigationRequest, "files", "documents", "my pdfs", "pdfs") -> {
                speak("Opening your documents.")
                launchActivity(PdfListActivity::class.java)
                true
            }

            containsAny(navigationRequest, "notes", "my notes", "notebook") -> {
                speak("Opening your notes.")
                launchActivity(NotesListActivity::class.java)
                true
            }

            containsAny(command, "new note", "create note", "add note", "likho") -> {
                val titlePart = textAfterCommand(command, 
                    "create note with title ", "create note titled ", 
                    "new note with title ", "add note called ", 
                    "create note and give title ", "create note give title ",
                    "create note title ", "new note title ", "note likho जिसका नाम है ")
                speak("Opening a new note.")
                val intent = Intent(this, NoteActivity::class.java)
                if (titlePart.isNotEmpty()) {
                    intent.putExtra("title", titlePart)
                }
                startActivity(intent)
                true
            }

            containsAny(navigationRequest, "scan", "camera", "scanner", "photo kheencho") -> {
                speak("Opening scanner.")
                launchActivity(CameraActivity::class.java)
                true
            }

            containsAny(navigationRequest, "merge", "combine", "merge pdf") -> {
                speak("Opening merge PDF tool.")
                launchActivity(MergePdfActivity::class.java)
                true
            }

            containsAny(navigationRequest, "ai assistant", "assistant", "chat", "bot", "pucho") -> {
                speak("Opening AI assistant.")
                launchActivity(AiChatActivity::class.java)
                true
            }

            containsAny(navigationRequest, "settings", "preferences", "config", "setting") -> {
                if (openSettingsFromVoice()) {
                    true
                } else {
                    speak("Opening settings.")
                    launchActivity(MainActivity::class.java, clearTop = true, openSettings = true)
                    true
                }
            }

            containsAny(command, "help", "voice help", "what can i say", "madad") -> {
                speak(voiceCommandHelp())
                true
            }

            containsAny(command, "go back", "back", "return", "piche", "close") -> {
                speak("Going back.")
                onBackPressedDispatcher.onBackPressed()
                true
            }

            containsAny(command, "autonomous mode on", "enable autonomous mode", "hands free mode on") -> {
                setAutonomousVoiceEnabled(true)
                speak("Autonomous voice mode enabled.")
                true
            }

            containsAny(command, "autonomous mode off", "disable autonomous mode") -> {
                appConfig.edit().putBoolean(KEY_AUTONOMOUS_VOICE, false).apply()
                speak("Autonomous voice mode disabled.")
                true
            }

            else -> false
        }
    }


    private fun launchActivity(target: Class<*>, clearTop: Boolean = false, openSettings: Boolean = false) {
        if (javaClass == target && !openSettings) return

        val intent = Intent(this, target).apply {
            if (clearTop) {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            if (openSettings) {
                putExtra(EXTRA_OPEN_SETTINGS, true)
            }
        }
        startActivity(intent)
    }

    private fun toggleVoiceAssistant() {
        if (isVoiceActiveForScreen) {
            isVoiceActiveForScreen = false
            stopListeningInternal()
            Toast.makeText(this, "Voice agent disabled for this screen.", Toast.LENGTH_SHORT).show()
        } else {
            enableVoiceAssistant(announce = true)
        }
        updateVoiceButtonState()
    }

    protected fun enableVoiceAssistant(announce: Boolean) {
        if (!isVoiceRecognitionSupported()) {
            Toast.makeText(this, "Speech recognition is not available on this device.", Toast.LENGTH_SHORT).show()
            updateVoiceButtonState()
            return
        }

        if (!hasAudioPermission()) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        isVoiceActiveForScreen = true
        startListeningInternal()
        updateVoiceButtonState()
        if (announce) {
            Toast.makeText(this, "Voice agent is listening.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startListeningInternal() {
        if (!hasAudioPermission() || !isVoiceRecognitionSupported()) return
        // Never listen while TTS is speaking — prevents echo feedback
        if (isTtsSpeaking) return

        initializeSpeechRecognizer()
        updateVoiceButtonState()
        speechHandler.removeCallbacksAndMessages(null)

        val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.startListening(listenIntent)
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }

    private fun stopListeningInternal() {
        speechHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.cancel()
        updateVoiceButtonState()
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun shouldKeepVoiceActive(): Boolean {
        return isVoiceActiveForScreen || isAutonomousVoiceEnabled()
    }

    private fun updateVoiceButtonState() {
        val button = existingVoiceButton ?: return
        val isSupported = isVoiceRecognitionSupported()
        val isActive = shouldKeepVoiceActive()

        button.isEnabled = isSupported
        button.alpha = if (isSupported) 1f else 0.55f

        when (button) {
            is ExtendedFloatingActionButton -> {
                button.setIconResource(if (isActive) android.R.drawable.ic_media_pause else R.drawable.ic_mic)
                button.text = if (isActive) getString(R.string.home_voice_live) else getString(R.string.fab_voice_text)
            }

            is FloatingActionButton -> {
                button.setImageResource(if (isActive) android.R.drawable.ic_media_pause else R.drawable.ic_mic)
                val tint = if (isActive) R.color.secondary_warm else R.color.primary_warm
                button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, tint))
            }

            else -> {
                button.isSelected = isActive
            }
        }
    }

    private fun dpToPx(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onResume() {
        super.onResume()
        if (!isVoiceMode()) {
            existingVoiceButton?.visibility = View.GONE
            return
        }
        if (shouldKeepVoiceActive() && hasAudioPermission()) {
            startListeningInternal()
        } else {
            updateVoiceButtonState()
        }
    }

    override fun onPause() {
        stopListeningInternal()
        super.onPause()
    }

    override fun onDestroy() {
        speechHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        existingVoiceButton = null
        super.onDestroy()
    }

    protected fun containsAny(command: String, vararg phrases: String): Boolean {
        return phrases.any { command.contains(it) }
    }

    private fun isVoiceRecognitionSupported(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(this)
    }
}
