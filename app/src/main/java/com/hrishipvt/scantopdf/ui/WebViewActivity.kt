package com.hrishipvt.scantopdf.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.databinding.ActivityWebViewBinding

class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebViewBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra("url") ?: "https://www.google.com"
        val title = intent.getStringExtra("title") ?: "Web Browser"

        binding.webToolbar.title = title
        setSupportActionBar(binding.webToolbar)
        binding.webToolbar.setNavigationOnClickListener { finish() }

        setupWebView(url)
        setupBackPressDispatcher()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(url: String) {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true

            // Keeps navigation inside the WebView rather than opening external browser
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }

            // Manage loading progress bar
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress == 100) {
                        binding.webProgressBar.visibility = View.GONE
                    } else {
                        binding.webProgressBar.visibility = View.VISIBLE
                        binding.webProgressBar.progress = newProgress
                    }
                }
            }

            loadUrl(url)
        }
    }

    private fun setupBackPressDispatcher() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }
}
