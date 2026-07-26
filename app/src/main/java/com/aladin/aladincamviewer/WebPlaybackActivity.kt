package com.aladin.aladincamviewer

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.net.Uri
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback

class WebPlaybackActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loader: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_playback)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
        TvFocusManager.install(this)

        webView = findViewById(R.id.playback_webview)
        loader = findViewById(R.id.web_loader)

        val ip = intent.getStringExtra("camera_ip").orEmpty().trim()
        if (!isPrivateAddress(ip)) {
            finish()
            return
        }
        
        // Optimize webview for camera web UI (which uses WebSockets/JS)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url ?: return true
                return target.scheme !in setOf("http", "https") || target.host != ip
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loader.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loader.visibility = View.GONE
                // Auto-fill login if possible or focus on password
            }
        }

        // Directly go to playback page if the camera supports hashtag routing
        webView.loadUrl("http://$ip/#playback")
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }

    private fun isPrivateAddress(value: String): Boolean = runCatching {
        val address = java.net.InetAddress.getByName(value)
        address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress
    }.getOrDefault(false)
}
