package com.hrishipvt.scantopdf.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.hrishipvt.scantopdf.adapter.NotificationHistoryAdapter
import com.hrishipvt.scantopdf.adapter.NotificationHistoryItem
import com.hrishipvt.scantopdf.databinding.ActivityAdminPanelBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminPanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPanelBinding
    private lateinit var functions: FirebaseFunctions
    private lateinit var firestore: FirebaseFirestore
    private val historyAdapter = NotificationHistoryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        functions = FirebaseFunctions.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupToolbar()
        setupHistoryList()
        setupSendButton()
        loadStats()
        loadHistory()
    }

    private fun setupToolbar() {
        binding.toolbarAdmin.setNavigationOnClickListener { finish() }
    }

    private fun setupHistoryList() {
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@AdminPanelActivity)
            adapter = historyAdapter
        }
    }

    private fun setupSendButton() {
        binding.btnSendNotification.setOnClickListener {
            val title = binding.edtNotifTitle.text.toString().trim()
            val message = binding.edtNotifMessage.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (message.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendNotification(title, message)
        }
    }

    private fun sendNotification(title: String, message: String) {
        binding.progressSend.visibility = View.VISIBLE
        binding.btnSendNotification.isEnabled = false

        val email = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown Admin"

        lifecycleScope.launch {
            try {
                com.hrishipvt.scantopdf.firebase.FcmDirectSender.sendNotification(
                    context = this@AdminPanelActivity,
                    title = title,
                    message = message,
                    adminEmail = email
                )

                // Back on main thread
                binding.progressSend.visibility = View.GONE
                binding.btnSendNotification.isEnabled = true
                binding.edtNotifTitle.text?.clear()
                binding.edtNotifMessage.text?.clear()

                Toast.makeText(this@AdminPanelActivity, "Notification sent to all users!", Toast.LENGTH_SHORT).show()

                // Refresh history
                loadStats()
                loadHistory()

            } catch (e: Exception) {
                binding.progressSend.visibility = View.GONE
                binding.btnSendNotification.isEnabled = true
                Toast.makeText(this@AdminPanelActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadStats() {
        // Load user count from Firestore
        firestore.collection("users").get()
            .addOnSuccessListener { snapshot ->
                binding.tvUserCount.text = snapshot.size().toString()
            }
            .addOnFailureListener {
                binding.tvUserCount.text = "—"
            }

        // Load notification count
        firestore.collection("notifications").get()
            .addOnSuccessListener { snapshot ->
                binding.tvNotifCount.text = snapshot.size().toString()
            }
            .addOnFailureListener {
                binding.tvNotifCount.text = "—"
            }
    }

    private fun loadHistory() {
        binding.progressHistory.visibility = View.VISIBLE
        binding.tvEmptyHistory.visibility = View.GONE

        firestore.collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressHistory.visibility = View.GONE

                val items = snapshot.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val message = doc.getString("message") ?: return@mapNotNull null
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    NotificationHistoryItem(title, message, timestamp)
                }

                if (items.isEmpty()) {
                    binding.tvEmptyHistory.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyHistory.visibility = View.GONE
                }

                historyAdapter.updateData(items)
            }
            .addOnFailureListener {
                binding.progressHistory.visibility = View.GONE
                binding.tvEmptyHistory.visibility = View.VISIBLE
                binding.tvEmptyHistory.text = "Failed to load history."
            }
    }
}
