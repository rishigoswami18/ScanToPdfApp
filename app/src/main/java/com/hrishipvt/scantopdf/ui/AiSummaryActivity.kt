package com.hrishipvt.scantopdf.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.hrishipvt.scantopdf.ai.GeminiApi
import com.hrishipvt.scantopdf.databinding.ActivityAiSummaryBinding
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity

class AiSummaryActivity : VoiceEnabledActivity() {

    private lateinit var binding: ActivityAiSummaryBinding
    private var selectedOperation = "Summarize"
    private var shouldReadResultWhenReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupVoiceAssistant()
        handleIntentData()
        setupListeners()
    }

    override fun voiceButtonBottomMarginDp(): Int = 32

    override fun voiceCommandHelp(): String {
        return "Try saying summarize, key points, tone, simplify, run AI process, analyze followed by text, or read result."
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        val directAnalysisText = textAfterCommand(rawCommand, "analyze ", "process this ", "summarize this ")

        return when {
            directAnalysisText.isNotEmpty() -> {
                binding.edtInput.setText(directAnalysisText)
                shouldReadResultWhenReady = true
                processAiTask(directAnalysisText)
                true
            }

            normalizedCommand.contains("key point") || normalizedCommand.contains("keyword") -> {
                binding.chipKeywords.performClick()
                speak("Key points mode selected.")
                true
            }

            normalizedCommand.contains("tone") -> {
                binding.chipTone.performClick()
                speak("Tone analysis mode selected.")
                true
            }

            normalizedCommand.contains("simplify") || normalizedCommand.contains("explain") -> {
                binding.chipExplain.performClick()
                speak("Simplify mode selected.")
                true
            }

            normalizedCommand.contains("summarize") || normalizedCommand.contains("summary") -> {
                binding.chipSummarize.performClick()
                speak("Summary mode selected.")
                true
            }

            normalizedCommand.contains("run") || normalizedCommand.contains("generate") || normalizedCommand.contains("process") -> {
                val inputText = binding.edtInput.text.toString().trim()
                if (inputText.isEmpty()) {
                    speak("Please add some text first.")
                } else {
                    shouldReadResultWhenReady = true
                    processAiTask(inputText)
                }
                true
            }

            normalizedCommand.contains("read result") || normalizedCommand.contains("read summary") -> {
                val summary = binding.txtSummary.text.toString().trim()
                if (summary.isNotEmpty()) {
                    speak(summary.take(320))
                } else {
                    speak("There is no result to read yet.")
                }
                true
            }

            else -> false
        }
    }

    override fun onUnknownVoiceCommand(rawCommand: String, normalizedCommand: String) {
        if (rawCommand.length < 12) {
            speak("I did not catch that. ${voiceCommandHelp()}")
            return
        }

        binding.edtInput.setText(rawCommand)
        shouldReadResultWhenReady = true
        speak("Analyzing that text now.")
        processAiTask(rawCommand)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun handleIntentData() {
        val extractedText = intent.getStringExtra("EXTRA_OCR_TEXT")
        if (!extractedText.isNullOrEmpty()) {
            binding.edtInput.setText(extractedText)
        }
    }

    private fun setupListeners() {
        binding.chipSummarize.setOnClickListener {
            selectedOperation = "Summarize"
            binding.txtResultTitle.text = "Summary"
        }
        binding.chipKeywords.setOnClickListener {
            selectedOperation = "Extract Key Points"
            binding.txtResultTitle.text = "Key Points"
        }
        binding.chipTone.setOnClickListener {
            selectedOperation = "Analyze Tone"
            binding.txtResultTitle.text = "Tone Analysis"
        }
        binding.chipExplain.setOnClickListener {
            selectedOperation = "Simplify/Explain"
            binding.txtResultTitle.text = "Simplified Explanation"
        }

        binding.btnGenerate.setOnClickListener {
            val inputText = binding.edtInput.text.toString().trim()

            if (inputText.isEmpty()) {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            processAiTask(inputText)
        }
    }

    private fun processAiTask(text: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.summaryCard.visibility = View.GONE

        val prompt = when (selectedOperation) {
            "Summarize" -> "Provide a concise summary of the following text:"
            "Extract Key Points" -> "Extract the main key points from this text in a bulleted list:"
            "Analyze Tone" -> "Analyze the sentiment and professional tone of this text:"
            "Simplify/Explain" -> "Explain the following text as if I am 10 years old:"
            else -> "Process this text:"
        }

        GeminiApi.processAiTask(
            prompt = prompt,
            content = text,
            onSuccess = { result ->
                binding.progressBar.visibility = View.GONE
                binding.summaryCard.visibility = View.VISIBLE
                binding.txtSummary.text = result
                if (shouldReadResultWhenReady) {
                    shouldReadResultWhenReady = false
                    speak(result.take(320))
                }
            },
            onError = { error ->
                binding.progressBar.visibility = View.GONE
                shouldReadResultWhenReady = false
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
