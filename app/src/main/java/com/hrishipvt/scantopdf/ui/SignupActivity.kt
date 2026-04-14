package com.hrishipvt.scantopdf.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.hrishipvt.scantopdf.databinding.ActivitySignupBinding
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity

class SignupActivity : VoiceEnabledActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        setupVoiceAssistant()
        setupListeners()
    }

    override fun voiceCommandHelp(): String {
        return "Try saying name followed by your full name, email followed by your address, password followed by your password, confirm password, create account, or sign in."
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        val nameInput = textAfterCommand(rawCommand, "name ", "full name ", "my name is ")
        val emailInput = textAfterCommand(rawCommand, "email ", "set email ", "my email is ")
        val passwordInput = textAfterCommand(rawCommand, "password ", "set password ", "my password is ")
        val confirmInput = textAfterCommand(rawCommand, "confirm ", "confirm password ", "confirmation ")

        return when {
            nameInput.isNotEmpty() -> {
                binding.edtName.setText(nameInput)
                binding.edtName.setSelection(binding.edtName.text?.length ?: 0)
                speak("Name updated.")
                true
            }

            emailInput.isNotEmpty() -> {
                binding.edtEmail.setText(normalizeSpokenEmail(emailInput))
                binding.edtEmail.setSelection(binding.edtEmail.text?.length ?: 0)
                speak("Email updated.")
                true
            }

            confirmInput.isNotEmpty() -> {
                binding.edtConfirmPassword.setText(confirmInput.replace(" ", ""))
                binding.edtConfirmPassword.setSelection(binding.edtConfirmPassword.text?.length ?: 0)
                speak("Confirmation password updated.")
                true
            }

            passwordInput.isNotEmpty() -> {
                binding.edtPassword.setText(passwordInput.replace(" ", ""))
                binding.edtPassword.setSelection(binding.edtPassword.text?.length ?: 0)
                speak("Password updated.")
                true
            }

            normalizedCommand.contains("create account") || normalizedCommand.contains("sign up") || normalizedCommand.contains("register") -> {
                handleSignup()
                true
            }

            normalizedCommand.contains("sign in") || normalizedCommand.contains("login") || normalizedCommand.contains("already have account") -> {
                finish()
                true
            }

            normalizedCommand.contains("status") -> {
                val status = buildString {
                    append(if (binding.edtName.text.isNullOrBlank()) "Name is empty. " else "Name is filled. ")
                    append(if (binding.edtEmail.text.isNullOrBlank()) "Email is empty. " else "Email is filled. ")
                    append(if (binding.edtPassword.text.isNullOrBlank()) "Password is empty. " else "Password is filled. ")
                    append(if (binding.edtConfirmPassword.text.isNullOrBlank()) "Confirm password is empty." else "Confirm password is filled.")
                }
                speak(status)
                true
            }

            else -> false
        }
    }

    private fun setupListeners() {
        binding.btnSignup.setOnClickListener {
            handleSignup()
        }

        binding.txtLogin.setOnClickListener {
            finish()
        }
    }

    private fun handleSignup() {
        val name = binding.edtName.text.toString().trim()
        val email = binding.edtEmail.text.toString().trim()
        val pass = binding.edtPassword.text.toString().trim()
        val confirm = binding.edtConfirmPassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            speak("Please complete all fields before creating the account.")
            return
        }

        if (pass.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            speak("Your password must be at least six characters long.")
            return
        }

        if (pass != confirm) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            speak("The password and confirmation password do not match.")
            return
        }

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()
                sendEmailVerification()
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                speak(it.message ?: "Account creation failed.")
            }
    }

    private fun sendEmailVerification() {
        val user = auth.currentUser
        user?.sendEmailVerification()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Verification email sent. Please check your inbox.", Toast.LENGTH_LONG).show()
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                val errorMessage = task.exception?.message ?: "Failed to send verification email"
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                speak(errorMessage)
            }
        }
    }
}
