package com.hrishipvt.scantopdf.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.hrishipvt.scantopdf.R

import com.hrishipvt.scantopdf.view.PdfTool
import com.google.firebase.auth.FirebaseAuth
import com.hrishipvt.scantopdf.adapter.PdfToolsAdapter
import com.hrishipvt.scantopdf.utils.FirebaseUploadUtils
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var isAutoUploadEnabled = true

    // Activity Result Launchers
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val recyclerView: RecyclerView = findViewById(R.id.rvPdfTools)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        val currentUser = auth.currentUser
        val accountTitle = if (currentUser != null) {
            currentUser.displayName ?: currentUser.email?.split("@")?.get(0) ?: "User"
        } else {
            "My Account"
        }

        val toolList = listOf(
            PdfTool(accountTitle, R.drawable.ic_user),
            PdfTool("AI Summary", R.drawable.ic_ai_summary),
            PdfTool("Image to PDF", R.drawable.ic_image_to_pdf),
            PdfTool("Merge PDF", R.drawable.ic_merge),
            PdfTool("Sign PDF", R.drawable.ic_sign),
            PdfTool("Encrypt PDF", R.drawable.ic_lock),
            PdfTool("Decrypt PDF", R.drawable.ic_unlock),
            PdfTool("Doc to PDF", R.drawable.ic_word_to_pdf),
            PdfTool("Take Notes", R.drawable.ic_note)
        )

        recyclerView.adapter = PdfToolsAdapter(toolList) { tool ->
            handleToolClick(tool, accountTitle)
        }

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    Toast.makeText(this, "Home Dashboard", Toast.LENGTH_SHORT).show()
                    true
                }
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
            "AI Summary" -> startActivity(Intent(this, CameraActivity::class.java))
            "Merge PDF" -> startActivity(Intent(this, MergePdfActivity::class.java))
            "Image to PDF" -> showImageSourceDialog()
            "Doc to PDF" -> showDocSourceDialog()
            "Sign PDF" -> openSignatureTool()
            "Encrypt PDF" -> openPasswordTool()
            "Take Notes" -> startActivity(Intent(this, NoteActivity::class.java))
            else -> Toast.makeText(this, "Opening ${tool.title}...", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Core Tool Logic ---

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
                if (password.isNotEmpty()) protectPdfWithPassword(pdfUri, password)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun saveSignatureToPdf(signatureBitmap: Bitmap, pdfUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                    val document = PDDocument.load(inputStream)
                    val page = document.getPage(0)
                    val xImage = LosslessFactory.createFromImage(document, signatureBitmap)

                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true).use { contentStream ->
                        contentStream.drawImage(xImage, 400f, 50f, 150f, 75f)
                    }

                    val outputFile = File(getExternalFilesDir(null), "signed_${System.currentTimeMillis()}.pdf")
                    val outputStream = FileOutputStream(outputFile)
                    document.save(outputStream)
                    document.close()
                    outputStream.close()

                    withContext(Dispatchers.Main) { onPdfGenerated(outputFile.absolutePath) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun protectPdfWithPassword(uri: Uri, userPass: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val document = PDDocument.load(inputStream)
                    val encryptionInfo = StandardProtectionPolicy("owner_pass", userPass, AccessPermission())
                    encryptionInfo.encryptionKeyLength = 128
                    document.protect(encryptionInfo)

                    val outputFile = File(getExternalFilesDir(null), "protected_${System.currentTimeMillis()}.pdf")
                    document.save(outputFile)
                    document.close()

                    withContext(Dispatchers.Main) { onPdfGenerated(outputFile.absolutePath) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun onPdfGenerated(localPath: String) {
        if (isAutoUploadEnabled) performCloudUpload(localPath)
        else Toast.makeText(this, "Saved locally: $localPath", Toast.LENGTH_LONG).show()
    }

    private fun performCloudUpload(path: String) {
        FirebaseUploadUtils.uploadPdfToCloud(path) { success, message ->
            val text = if (success) "Cloud Sync Complete!" else "Cloud Error: $message"
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    // --- UI Helpers ---

    private fun showSettingsDialog() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, root, false)
        val switchAuto: SwitchMaterial = dialogView.findViewById(R.id.switchAutoUpload)
        val switchDark: SwitchMaterial = dialogView.findViewById(R.id.switchDarkMode)

        val prefs = getSharedPreferences("AppConfig", MODE_PRIVATE)
        switchAuto.isChecked = prefs.getBoolean("AUTO_UPLOAD", true)
        switchDark.isChecked = prefs.getBoolean("DARK_MODE", false)

        MaterialAlertDialogBuilder(this)
            .setTitle("App Settings")
            .setView(dialogView)
            .setPositiveButton("Done") { _, _ ->
                val isDark = switchDark.isChecked
                isAutoUploadEnabled = switchAuto.isChecked
                prefs.edit().putBoolean("AUTO_UPLOAD", isAutoUploadEnabled).putBoolean("DARK_MODE", isDark).apply()
                AppCompatDelegate.setDefaultNightMode(if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            }.show()
    }

    private fun showImageSourceDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val root = findViewById<ViewGroup>(android.R.id.content)
        val view = layoutInflater.inflate(R.layout.dialog_image_source, root, false)

        view.findViewById<LinearLayout>(R.id.layoutCamera).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java)); dialog.dismiss()
        }
        view.findViewById<LinearLayout>(R.id.layoutGallery).setOnClickListener {
            pickMultipleImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            dialog.dismiss()
        }
        dialog.setContentView(view); dialog.show()
    }

    private fun showDocSourceDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val root = findViewById<ViewGroup>(android.R.id.content)
        val view = layoutInflater.inflate(R.layout.dialog_doc_source, root, false)

        view.findViewById<LinearLayout>(R.id.layoutSelectDoc).setOnClickListener {
            pickDocument.launch(arrayOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "application/pdf"))
            dialog.dismiss()
        }
        dialog.setContentView(view); dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
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
            // FIX: Ensure this ID matches your main_menu.xml exactly
            R.id.ai_chat -> {
                val intent = Intent(this, AiChatActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_login -> {
                startActivity(Intent(this, LoginActivity::class.java))
                true
            }
            R.id.action_logout -> {
                auth.signOut()
                invalidateOptionsMenu() // Refresh menu to show Login again
                Toast.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}