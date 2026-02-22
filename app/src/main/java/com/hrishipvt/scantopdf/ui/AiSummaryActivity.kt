package com.hrishipvt.scantopdf.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.ai.GeminiApi

class AiSummaryActivity : AppCompatActivity() {

    private lateinit var tvSummary: TextView
    private lateinit var progressBar: ProgressBar


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_summary)

        val edtInput = findViewById<EditText>(R.id.edtInput)
        val btnGenerate = findViewById<MaterialButton>(R.id.btnGenerate)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val txtSummary = findViewById<TextView>(R.id.txtSummary)

        // FIX: Retrieve the text and handle null safety

        // 2. Get the string using the EXACT same key
        val extractedText = intent.getStringExtra("EXTRA_OCR_TEXT")

        // 3. Paste the text
        if (!extractedText.isNullOrEmpty()) {
            edtInput.setText(extractedText) // This performs the "paste"
        }



        // FIX: Only set text if extractedText is not null
        extractedText?.let {
            if (it.isNotBlank()) {
                edtInput.setText(it)
            }
        }

        btnGenerate.setOnClickListener {
            val inputText = edtInput.text.toString().trim()

            // FIX: Correct way to check for empty input
            if (inputText.isEmpty()) {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Start AI Process
            generateSummary(inputText, progressBar, txtSummary)
        }
    }

    private fun generateSummary(text: String, progress: ProgressBar, output: TextView) {
        progress.visibility = View.VISIBLE
        output.text = "AI is thinking..."

        GeminiApi.chatWithFile(
            userMessage = text,
            fileUri = null, // In summary mode, we send the text already extracted
            contentResolver = contentResolver,
            onSuccess = { summary ->
                progressBar.visibility = View.GONE
                tvSummary.text = summary
            },
            onError = { error ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        )
    }
}