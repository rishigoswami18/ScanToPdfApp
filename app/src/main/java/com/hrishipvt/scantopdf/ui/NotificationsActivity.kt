package com.hrishipvt.scantopdf.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.adapter.NotificationAdapter
import com.hrishipvt.scantopdf.data.NotificationDatabase
import com.hrishipvt.scantopdf.databinding.ActivityNotificationsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private val db by lazy { NotificationDatabase.getDatabase(this) }
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupList()
        observeNotifications()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarNotifications)
        binding.toolbarNotifications.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun setupList() {
        adapter = NotificationAdapter { notification ->
            // Mark as read when clicked
            lifecycleScope.launch {
                db.notificationDao().markAsRead(notification.id)
            }
        }

        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationsActivity)
            adapter = this@NotificationsActivity.adapter
        }
    }

    private fun observeNotifications() {
        lifecycleScope.launch {
            db.notificationDao().getAllNotifications().collectLatest { notifications ->
                adapter.submitList(notifications)

                if (notifications.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.rvNotifications.visibility = View.GONE
                } else {
                    binding.layoutEmpty.visibility = View.GONE
                    binding.rvNotifications.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, MENU_MARK_ALL_READ, 0, "Mark all read")
        menu?.add(0, MENU_CLEAR_ALL, 1, "Clear all")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_MARK_ALL_READ -> {
                lifecycleScope.launch {
                    db.notificationDao().markAllAsRead()
                    Toast.makeText(this@NotificationsActivity, "All marked as read", Toast.LENGTH_SHORT).show()
                }
                true
            }
            MENU_CLEAR_ALL -> {
                lifecycleScope.launch {
                    db.notificationDao().deleteAll()
                    Toast.makeText(this@NotificationsActivity, "All notifications cleared", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val MENU_MARK_ALL_READ = 100
        private const val MENU_CLEAR_ALL = 101
    }
}
