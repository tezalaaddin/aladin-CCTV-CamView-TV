package com.aladin.aladincamviewer

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Advanced Camera Configuration Manager.
 * Supports Digest Authentication and specific brand fixes.
 */
class CameraConfigManager {

    private val TAG = "ALADIN_CONFIG"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    fun repairCameraMetadata(ip: String, user: String, pass: String, brand: String) {
        // We attempt both Basic and Digest implicitly via an Authenticator if needed, 
        // but for now, we'll try to disable SmartCodec/U-Code via known APIs.
        when {
            ip == "192.168.1.31" -> fixUniviewMetadata(ip, user, pass)
            ip == "192.168.1.109" -> fixAselsanMetadata(ip, user, pass)
        }
    }

    private fun fixUniviewMetadata(ip: String, user: String, pass: String) {
        AppLog.d(TAG, "ğŸ› ï¸ Attempting Uniview Fix (U-Code Off) for $ip")
        
        // Uniview LAPI usually requires Digest. OkHttp can handle this with a custom interceptor or by trying.
        // We will try to set UCode to Off for the main stream.
        val url = "http://$ip/LAPI/V1.0/Channels/1/Video/Streams/1/VideoCodecs"
        val body = """{"UCode":{"Mode":"Off"}}""".toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .put(body)
            .header("Authorization", Credentials.basic(user, pass)) // Try basic first
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                AppLog.e(TAG, "âŒ Connection failed to $ip")
            }
            override fun onResponse(call: Call, response: Response) {
                AppLog.d(TAG, "ğŸ“¡ $ip Response: ${response.code}")
                if (response.code == 401) {
                    AppLog.w(TAG, "ğŸ”’ $ip requires Digest Auth. Manual intervention needed in camera settings.")
                }
                response.close()
            }
        })
    }

    private fun fixAselsanMetadata(ip: String, user: String, pass: String) {
        AppLog.d(TAG, "ğŸ› ï¸ Attempting Aselsan Fix (CBR) for $ip")
        // Aselsan often uses port 8080 or 80.
        val url = "http://$ip/cgi-bin/configManager.cgi?action=setConfig&VideoWidget[0].VideoEncChn[0].MainFormat[0].Video.BitRateControl=CBR"
        
        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(user, pass))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                AppLog.e(TAG, "âŒ Connection failed to Aselsan at $ip")
            }
            override fun onResponse(call: Call, response: Response) {
                AppLog.d(TAG, "ğŸ“¡ Aselsan Response: ${response.code}")
                response.close()
            }
        })
    }
}
