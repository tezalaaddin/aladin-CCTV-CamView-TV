package com.aladin.aladincamviewer

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class WebPlaybackActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loader: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_playback)

        webView = findViewById(R.id.playback_webview)
        loader = findViewById(R.id.web_loader)

        val ip = intent.getStringExtra("camera_ip") ?: ""
        
        // Optimize webview for camera web UI (which uses WebSockets/JS)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.webViewClient = object : WebViewClient() {
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

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}