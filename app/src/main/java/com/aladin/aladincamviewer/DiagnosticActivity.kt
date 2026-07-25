package com.aladin.aladincamviewer

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Plays the first two cameras from the local development database.
 * No camera address or credential may be hardcoded in this source file.
 */
class DiagnosticActivity : AppCompatActivity() {
    private val tag = "ALADIN_DIAG"
    private lateinit var logView: TextView
    private var manager1: CctvPlayerManager? = null
    private var manager2: CctvPlayerManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostic)
        logView = findViewById(R.id.diag_log)
        appendLog("RTSP diagnostic mode (LibVLC)")

        lifecycleScope.launch {
            val cameras = AppDatabase.getDatabase(applicationContext)
                .cameraDao()
                .getAllCameras()
                .first()
                .take(2)

            if (cameras.isEmpty()) {
                appendLog("No configured camera found. Add a camera first.")
                return@launch
            }

            cameras.getOrNull(0)?.let { camera ->
                appendLog("Starting camera 1: ${camera.name}")
                manager1 = createManager(1)
                manager1?.attachView(findViewById<VLCVideoLayout>(R.id.diag_player_1))
                manager1?.playStream(camera.subStreamUrl.ifBlank { camera.mainStreamUrl })
            }

            cameras.getOrNull(1)?.let { camera ->
                appendLog("Starting camera 2: ${camera.name}")
                manager2 = createManager(2)
                manager2?.attachView(findViewById<VLCVideoLayout>(R.id.diag_player_2))
                manager2?.playStream(camera.subStreamUrl.ifBlank { camera.mainStreamUrl })
            }
        }
    }

    private fun createManager(index: Int) = CctvPlayerManager { loading, error ->
        when {
            error != null -> appendLog("Camera $index: $error")
            loading -> appendLog("Camera $index: connecting")
            else -> appendLog("Camera $index: playing")
        }
    }.also { it.initializePlayer(isSubStream = true) }

    private fun appendLog(message: String) {
        runOnUiThread {
            logView.append("\n$message")
            Log.d(tag, message)
        }
    }

    override fun onDestroy() {
        manager1?.releasePlayer()
        manager2?.releasePlayer()
        super.onDestroy()
    }
}
