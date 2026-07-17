package com.aladin.aladincamviewer

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optimizes camera settings (H.264, FrameRate, SmartCodec) via ONVIF/CGI
 * to ensure smooth playback on low-end TV hardware.
 */
class CameraConfigManager(private val camera: CameraModel) {

    private val TAG = "CameraConfigManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Sends "The Fix" command to ensure camera uses H.264 instead of H.265+ 
     * which often crashes entry-level TV boxes.
     */
    fun optimizeForTV() {
        scope.launch {
            // If AJCloud/Uniview, try mark-specific CGI
            if (camera.brand == "AJCloud" || camera.brand == "Custom") {
                setH264ViaCgi()
            }
        }
    }

    private fun setH264ViaCgi() {
        try {
            // Example for AJCloud/Uniview style CGI to disable SmartCodec/H.265
            val url = URL("http://${camera.ipAddress}/cgi-bin/configManager.cgi?action=setConfig&VideoEncode[0].MainStream.Compression=H.264")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            if (conn.responseCode == 200) Log.d(TAG, "CGI Optimization Success")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
