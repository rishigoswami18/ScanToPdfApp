package com.hrishipvt.scantopdf.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.databinding.ActivityLoginBinding
import com.hrishipvt.scantopdf.voice.VoiceEnabledActivity

class LoginActivity : VoiceEnabledActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                val idToken = account?.idToken
                if (idToken.isNullOrBlank()) {
                    Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
                    speak("Google sign in did not return an ID token.")
                } else {
                    firebaseAuthWithGoogle(idToken)
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show()
                speak("Google sign in failed.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            goToMain()
            return
        }

        setupGoogleSignIn()
        setupVoiceAssistant()
        setupListeners()
    }

    override fun voiceCommandHelp(): String {
        return "Try saying email followed by your address, password followed by your password, sign in, google sign in, or create account."
    }

    override fun handleScreenVoiceCommand(rawCommand: String, normalizedCommand: String): Boolean {
        val emailInput = textAfterCommand(rawCommand, "email ", "set email ", "my email is ")
        val passwordInput = textAfterCommand(rawCommand, "password ", "set password ", "my password is ")

        return when {
            emailInput.isNotEmpty() -> {
                binding.edtEmail.setText(normalizeSpokenEmail(emailInput))
                binding.edtEmail.setSelection(binding.edtEmail.text?.length ?: 0)
                speak("Email updated.")
                true
            }

            passwordInput.isNotEmpty() -> {
                binding.edtPassword.setText(passwordInput.replace(" ", ""))
                binding.edtPassword.setSelection(binding.edtPassword.text?.length ?: 0)
                speak("Password updated.")
                true
            }

            normalizedCommand.contains("google") -> {
                speak("Opening Google sign in.")
                binding.btnGoogleSignIn.performClick()
                true
            }

            normalizedCommand.contains("sign in") || normalizedCommand.contains("log in") || normalizedCommand == "login" -> {
                emailLogin()
                true
            }

            normalizedCommand.contains("create account") || normalizedCommand.contains("sign up") || normalizedCommand.contains("register") -> {
                startActivity(Intent(this, SignupActivity::class.java))
                true
            }

            normalizedCommand.contains("status") -> {
                val emailStatus = if (binding.edtEmail.text.isNullOrBlank()) "Email is empty." else "Email is filled."
                val passwordStatus = if (binding.edtPassword.text.isNullOrBlank()) "Password is empty." else "Password is filled."
                speak("$emailStatus $passwordStatus")
                true
            }

            else -> false
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupListeners() {
        binding.btnGoogleSignIn.setOnClickListener {
            googleLauncher.launch(googleSignInClient.signInIntent)
        }

        binding.txtSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        binding.btnLogin.setOnClickListener {
            emailLogin()
        }
    }

    private fun emailLogin() {
        val email = binding.edtEmail.text.toString().trim()
        val password = binding.edtPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email & password", Toast.LENGTH_SHORT).show()
            speak("Please provide both email and password first.")
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { task ->
                if (task.user?.isEmailVerified == true) {
                    goToMain()
                } else {
                    Toast.makeText(this, "Please verify your email first", Toast.LENGTH_SHORT).show()
                    speak("Please verify your email before signing in.")
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                speak(it.message ?: "Sign in failed.")
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { goToMain() }
            .addOnFailureListener {
                Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                speak("Authentication failed.")
            }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
