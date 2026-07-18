package com.aladin.aladincamviewer

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Diagnostic Activity using LibVLC for robust playback.
 */
class DiagnosticActivity : AppCompatActivity() {

    private val TAG = "ALADIN_DIAG"
    private lateinit var logView: TextView
    
    private var manager1: CctvPlayerManager? = null
    private var manager2: CctvPlayerManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostic)
        logView = findViewById(R.id.diag_log)

        appendLog("🚀 DIAGNOSTIC MODE V9 (LibVLC Engine)")
        
        lifecycleScope.launch {
            delay(1000)

            // Test Cam 1 (Redline)
            manager1 = CctvPlayerManager(
                onStateChanged = { loading, err ->
                    runOnUiThread {
                        if (err != null) appendLog("❌ Cam 1 Error: $err")
                        else if (!loading) appendLog("✅ Cam 1 READY (Playing)!")
                    }
                }
            )
            findViewById<VLCVideoLayout>(R.id.diag_player_1).let { manager1?.attachView(it) }
            manager1?.playStream("rtsp://admin:Azra2010-@192.168.1.31/media/video1")

            delay(3000)

            // Test Cam 2 (Aselsan)
            manager2 = CctvPlayerManager(
                onStateChanged = { loading, err ->
                    runOnUiThread {
                        if (err != null) appendLog("❌ Cam 2 Error: $err")
                        else if (!loading) appendLog("✅ Cam 2 READY (Playing)!")
                    }
                }
            )
            findViewById<VLCVideoLayout>(R.id.diag_player_2).let { manager2?.attachView(it) }
            manager2?.playStream("rtsp://admin:Azra2010-@192.168.1.109:554/stream2")
        }
    }

    private fun appendLog(msg: String) {
        runOnUiThread {
            logView.append("\n$msg")
            Log.d(TAG, msg)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        manager1?.releasePlayer()
        manager2?.releasePlayer()
    }
}
