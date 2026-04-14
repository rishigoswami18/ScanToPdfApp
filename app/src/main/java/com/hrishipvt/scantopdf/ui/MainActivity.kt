package com.hrishipvt.scantopdf.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.adapter.PdfToolsAdapter
import com.hrishipvt.scantopdf.ai.GeminiApi
import com.hrishipvt.scantopdf.databinding.ActivityMainBinding
import com.hrishipvt.scantopdf.utils.FirebaseUploadUtils
import com.hrishipvt.scantopdf.view.PdfTool
import com.hrishipvt.scantopdf.viewmodel.NoteViewModel
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale

class MainActivity : VoiceEnabledActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private var isAutoUploadEnabled = true

    private val noteViewModel: NoteViewModel by viewModels()

    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val intent = Intent(this, PreviewActivity::class.java).apply {
                putExtra("selected_doc_uri", it)
                putExtra("is_doc_conversion", true)
            }
            startActivity(intent)
        }
    }

    private val pickMultipleImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            val intent = Intent(this, PreviewActivity::class.java).apply {
                putParcelableArrayListExtra("selected_uris", ArrayList(uris))
            }
            startActivity(intent)
        }
    }

    private val pickPdfForSignature = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val intent = Intent(this, SignatureActivity::class.java).apply {
                putExtra("pdf_uri", it.toString())
            }
            startActivity(intent)
        }
    }

    private val pickPdfForPassword = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { showPasswordInputDialog(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyStoredUiPreferences()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        loadStoredSettings()
        setupToolbar()
        setupVoiceAssistant()
        setupDashboardHeader()
        setupQuickActions()
        setupPdfTools()
        setupBottomNavigation()
        setupBanner()
        setupPresentationAssist()
        observeNotesCount()
        refreshPdfCount()
        handleNavigationExtras()
    }

    private fun applyStoredUiPreferences() {
        val prefs = getSharedPreferences(APP_CONFIG_PREFS, MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("DARK_MODE", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun loadStoredSettings() {
        val prefs = getSharedPreferences(APP_CONFIG_PREFS, MODE_PRIVATE)
        isAutoUploadEnabled = prefs.getBoolean("AUTO_UPLOAD", true)
    }

    private fun handleNavigationExtras() {
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            binding.root.post {
                intent.removeExtra(EXTRA_OPEN_SETTINGS)
                showSettingsDialog()
            }
        }
    }

    private fun observeNotesCount() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                noteViewModel.notes.collect { notes ->
                    binding.tvNotesCount.text = notes.size.toString()
                }
            }
        }
    }

    private fun refreshPdfCount() {
        lifecycleScope.launch(Dispatchers.IO) {
            val knownPdfPaths = linkedSetOf<String>()
            val locations = listOf(
                getExternalFilesDir(null),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            )

            locations.forEach { directory ->
                directory?.listFiles { file ->
                    file.extension.equals("pdf", ignoreCase = true)
                }?.forEach { file ->
                    knownPdfPaths.add(file.absolutePath)
                }
            }

            withContext(Dispatchers.Main) {
                binding.tvPdfCount.text = knownPdfPaths.size.toString()
            }
        }
    }

    private fun setupDashboardHeader() {
        val user = auth.currentUser
        val displayName = user?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: user?.email
                ?.substringBefore("@")
                ?.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }

        binding.tvGreeting.text = if (displayName != null) {
            getString(R.string.home_greeting_personal, currentGreetingPrefix(), displayName)
        } else {
            getString(R.string.home_greeting_generic, currentGreetingPrefix())
        }

        binding.tvWorkspaceSummary.text = if (user != null) {
            getString(R.string.home_workspace_summary_signed_in)
        } else {
            getString(R.string.home_workspace_summary_guest)
        }
    }

    private fun currentGreetingPrefix(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    private fun setupQuickActions() {
        binding.btnQuickScan.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        binding.btnQuickFiles.setOnClickListener {
            startActivity(Intent(this, PdfListActivity::class.java))
        }

        binding.cardStatPdf.setOnClickListener {
            startActivity(Intent(this, PdfListActivity::class.java))
        }

        binding.cardStatNotes.setOnClickListener {
            startActivity(Intent(this, NotesListActivity::class.java))
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupBanner() {
        binding.btnTryAi.setOnClickListener {
            startActivity(Intent(this, AiChatActivity::class.java))
        }
    }

    private fun setupPresentationAssist() {
        binding.btnPresentationGuide.setOnClickListener {
            showPresentationGuideDialog()
        }

        binding.btnPresentationPitch.setOnClickListener {
            showPresentationPitchDialog()
        }
    }

    private fun setupPdfTools() {
        val currentUser = auth.currentUser
        val accountTitle = currentUser?.displayName
            ?: currentUser?.email?.substringBefore("@")?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            ?: "Account"

        val toolList = listOf(
            PdfTool("Image to PDF", "Capture receipts, notes, and documents into clean PDFs.", R.drawable.ic_image_to_pdf, R.color.primary_warm),
            PdfTool("Merge PDF", "Combine multiple files into a single organized document.", R.drawable.ic_merge, R.color.secondary_warm),
            PdfTool("Take Notes", "Write, save, and export notes for later reference.", R.drawable.ic_note, R.color.accent_warm),
            PdfTool("Sign PDF", "Add a polished signature before sharing final files.", R.drawable.ic_sign, R.color.primary_dark_warm),
            PdfTool("Doc to PDF", "Convert Word and text documents into portable PDFs.", R.drawable.ic_word_to_pdf, R.color.primary_warm),
            PdfTool("Encrypt PDF", "Protect sensitive files with a password before sharing.", R.drawable.ic_lock, R.color.primary_red),
            PdfTool("AI Assistant", "Summaries, explanations, and questions for every document.", R.drawable.ic_ai_summary, R.color.secondary_warm),
            PdfTool(accountTitle, if (currentUser != null) "Manage your access and review signed-in account details." else "Sign in to unlock sync, backup, and smarter workflows.", R.drawable.ic_user, R.color.accent_warm)
        )

        binding.rvPdfTools.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = PdfToolsAdapter(toolList) { tool ->
                handleToolClick(tool, accountTitle)
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_files -> {
                    startActivity(Intent(this, PdfListActivity::class.java))
                    true
                }

                R.id.nav_settings -> {
                    showSettingsDialog()
                    true
                }

                else -> false
            }
        }
    }

    private fun handleToolClick(tool: PdfTool, accountTitle: String) {
        when (tool.title) {
            accountTitle -> {
                if (auth.currentUser != null) {
                    Toast.makeText(this, "Logged in as ${auth.currentUser?.email}", Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
            }

            "AI Assistant" -> startActivity(Intent(this, AiChatActivity::class.java))
            "Merge PDF" -> startActivity(Intent(this, MergePdfActivity::class.java))
            "Image to PDF" -> showImageSourceDialog()
            "Doc to PDF" -> showDocSourceDialog()
            "Sign PDF" -> openSignatureTool()
            "Encrypt PDF" -> openPasswordTool()
            "Take Notes" -> startActivity(Intent(this, NotesListActivity::class.java))
            else -> Toast.makeText(this, "${tool.title} will be available soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSignatureTool() {
        pickPdfForSignature.launch(arrayOf("application/pdf"))
    }

    private fun openPasswordTool() {
        pickPdfForPassword.launch(arrayOf("application/pdf"))
    }

    private fun showPasswordInputDialog(pdfUri: Uri) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Enter Password"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Protect PDF")
            .setView(input)
            .setPositiveButton("Encrypt") { _, _ ->
                val password = input.text.toString()
                if (password.isNotEmpty()) {
                    protectPdfWithPassword(pdfUri, password)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun protectPdfWithPassword(uri: Uri, userPass: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val document = PDDocument.load(inputStream)
                    val encryptionInfo = StandardProtectionPolicy("owner_pass", userPass, AccessPermission())
                    encryptionInfo.encryptionKeyLength = 128
                    document.protect(encryptionInfo)

                    val outputDirectory = getExternalFilesDir(null) ?: filesDir
                    val outputFile = File(outputDirectory, "protected_${System.currentTimeMillis()}.pdf")
                    document.save(outputFile)
                    document.close()

                    withContext(Dispatchers.Main) {
                        onPdfGenerated(outputFile.absolutePath)
                    }
                }
            } catch (error: Exception) {
                error.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to protect PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onPdfGenerated(localPath: String) {
        if (isAutoUploadEnabled) {
            performCloudUpload(localPath)
        } else {
            Toast.makeText(this, "Saved locally: $localPath", Toast.LENGTH_LONG).show()
        }
    }

    private fun performCloudUpload(path: String) {
        FirebaseUploadUtils.uploadPdfToCloud(path) { success, message ->
            runOnUiThread {
                val text = if (success) "Cloud Sync Complete!" else "Cloud Error: $message"
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val switchAuto: SwitchMaterial = dialogView.findViewById(R.id.switchAutoUpload)
        val switchDark: SwitchMaterial = dialogView.findViewById(R.id.switchDarkMode)
        val switchAutonomousVoice: SwitchMaterial = dialogView.findViewById(R.id.switchAutonomousVoice)

        val prefs = getSharedPreferences(APP_CONFIG_PREFS, MODE_PRIVATE)
        switchAuto.isChecked = prefs.getBoolean("AUTO_UPLOAD", true)
        switchDark.isChecked = prefs.getBoolean("DARK_MODE", false)
        switchAutonomousVoice.isChecked = isAutonomousVoiceEnabled()

        MaterialAlertDialogBuilder(this)
            .setTitle("App Settings")
            .setView(dialogView)
            .setPositiveButton("Done") { _, _ ->
                val isDark = switchDark.isChecked
                isAutoUploadEnabled = switchAuto.isChecked

                prefs.edit()
                    .putBoolean("AUTO_UPLOAD", isAutoUploadEnabled)
                    .putBoolean("DARK_MODE", isDark)
                    .apply()

                setAutonomousVoiceEnabled(switchAutonomousVoice.isChecked)
                AppCompatDelegate.setDefaultNightMode(
                    if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
            .show()
    }

    private fun showImageSourceDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_image_source, binding.root, false)

        view.findViewById<LinearLayout>(R.id.layoutCamera).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
            dialog.dismiss()
        }

        view.findViewById<LinearLayout>(R.id.layoutGallery).setOnClickListener {
            pickMultipleImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showDocSourceDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_doc_source, binding.root, false)

        view.findViewById<LinearLayout>(R.id.layoutSelectDoc).setOnClickListener {
            pickDocument.launch(
                arrayOf(
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain",
                    "application/pdf"
                )
            )
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showAboutAppDialog() {
        val message = """
            ScanToPdf is a modern document workspace built for scanning, organizing, protecting, and understanding files from one place.

            Key capabilities:
            - Scan documents from camera or gallery
            - Merge, protect, and sign PDF files
            - Create notes and export them as PDF
            - Use AI to summarize and discuss document content
            - Run hands-free with the autonomous voice agent
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("About ScanToPdf")
            .setMessage(message)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showPresentationGuideDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.presentation_guide_title)
            .setMessage(getString(R.string.presentation_guide_message))
            .setPositiveButton(R.string.practice_with_voice) { _, _ ->
                speak(getString(R.string.presentation_pitch_spoken))
            }
            .setNeutralButton(R.string.presentation_demo_ai) { _, _ ->
                openAiPresentationDemo()
            }
            .setNegativeButton(R.string.close_label, null)
            .show()
    }

    private fun showPresentationPitchDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.presentation_pitch_title)
            .setMessage(getString(R.string.presentation_pitch_body))
            .setPositiveButton(R.string.practice_with_voice) { _, _ ->
                speak(getString(R.string.presentation_pitch_spoken))
            }
            .setNeutralButton(R.string.presentation_demo_scan) { _, _ ->
                startActivity(Intent(this, CameraActivity::class.java))
            }
            .setNegativeButton(R.string.close_label, null)
            .show()
    }

    private fun openAiPresentationDemo() {
        val intent = Intent(this, AiChatActivity::class.java).apply {
            putExtra(AiChatActivity.EXTRA_STARTER_PROMPT, getString(R.string.presentation_ai_prompt))
            putExtra(AiChatActivity.EXTRA_AUTO_SEND, true)
        }
        startActivity(intent)
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        return when {
            normalizedCommand.contains("presentation") || normalizedCommand.contains("class demo") || normalizedCommand.contains("demo guide") -> {
                showPresentationGuideDialog()
                true
            }

            normalizedCommand.contains("pitch") || normalizedCommand.contains("practice talk") -> {
                showPresentationPitchDialog()
                true
            }

            normalizedCommand.contains("image to pdf") || normalizedCommand.contains("gallery") -> {
                speak("Opening image import.")
                showImageSourceDialog()
                true
            }

            normalizedCommand.contains("sign") -> {
                speak("Opening PDF signature tool.")
                openSignatureTool()
                true
            }

            normalizedCommand.contains("encrypt") || normalizedCommand.contains("protect") || normalizedCommand.contains("lock") -> {
                speak("Opening PDF protection.")
                openPasswordTool()
                true
            }

            normalizedCommand.contains("doc") || normalizedCommand.contains("word") -> {
                speak("Opening document import.")
                showDocSourceDialog()
                true
            }

            normalizedCommand.contains("status") || normalizedCommand.contains("workspace summary") -> {
                speak("You currently have ${binding.tvPdfCount.text} PDFs and ${binding.tvNotesCount.text} notes in your workspace.")
                true
            }

            normalizedCommand.contains("account") || normalizedCommand.contains("profile") -> {
                if (auth.currentUser != null) {
                    speak("You are signed in as ${auth.currentUser?.email}.")
                } else {
                    speak("You are not signed in. Opening login.")
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                true
            }

            else -> false
        }
    }

    override fun onUnknownVoiceCommand(rawCommand: String, normalizedCommand: String) {
        speak("Let me think about that.")
        GeminiApi.processAiTask(
            prompt = "You are a smart autonomous voice assistant inside a mobile document productivity app. The user said: '$rawCommand'. Respond briefly in one or two short sentences.",
            content = "",
            onSuccess = { answer -> speak(answer.replace("*", "")) },
            onError = { speak("I could not process that request right now.") }
        )
    }

    override fun openSettingsFromVoice(): Boolean {
        showSettingsDialog()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val isLoggedIn = auth.currentUser != null
        menu?.findItem(R.id.action_login)?.isVisible = !isLoggedIn
        menu?.findItem(R.id.action_logout)?.isVisible = isLoggedIn
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }

            R.id.action_view_pdf -> {
                startActivity(Intent(this, PdfListActivity::class.java))
                true
            }

            R.id.ai_chat -> {
                startActivity(Intent(this, AiChatActivity::class.java))
                true
            }

            R.id.action_help -> {
                showAboutAppDialog()
                true
            }

            R.id.action_login -> {
                startActivity(Intent(this, LoginActivity::class.java))
                true
            }

            R.id.action_logout -> {
                auth.signOut()
                setupDashboardHeader()
                setupPdfTools()
                invalidateOptionsMenu()
                Toast.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        setupDashboardHeader()
        setupPdfTools()
        refreshPdfCount()
        invalidateOptionsMenu()
    }
}
