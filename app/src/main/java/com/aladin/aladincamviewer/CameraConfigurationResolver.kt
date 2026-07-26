package com.aladin.aladincamviewer

import android.util.Log
import android.content.Context

class CameraConfigurationResolver(private val context: Context) {
    data class Input(
        val ip: String,
        val username: String,
        val password: String,
        val onvifUsername: String? = null,
        val onvifPassword: String? = null,
        val brandHint: String? = null,
        val uuid: String? = null,
        val mac: String? = null
    )

    data class Result(
        val verified: Boolean,
        val source: String,
        val brand: String,
        val model: String?,
        val firmware: String?,
        val serial: String?,
        val mainUrl: String?,
        val subUrl: String?,
        val ptzSupported: Boolean,
        val uuid: String?,
        val mac: String?,
        val message: String
    )

    suspend fun resolve(input: Input): Result {
        Log.i(TAG, "Configuration started ip=${input.ip} brandHint=${input.brandHint.orEmpty()}")
        var profileMain: String? = null
        var profileSub: String? = null
        for ((main, sub) in CameraBrandProfiles.candidates(input.brandHint, input.ip, input.username, input.password)) {
            if (!isPlayable(main, input)) continue
            profileMain = main
            profileSub = if (isPlayable(sub, input)) sub else null
            Log.i(TAG, "Brand profile accepted ip=${input.ip} subStreamVerified=${profileSub != null}")
            break
        }

        val onvifUser = input.onvifUsername ?: input.username
        val onvifPass = input.onvifPassword ?: input.password
        val onvif = OnvifManager(input.ip, onvifUser, onvifPass).getDeviceDetails()
        Log.i(TAG, "ONVIF metadata ip=${input.ip} authenticated=${onvif != null} separateCredentials=${input.onvifUsername != null}")

        if (profileMain != null) {
            return Result(
                true, "BRAND_PROFILE", input.brandHint?.takeUnless { it == "Generic" } ?: "Compatible camera",
                onvif?.model, onvif?.firmware, onvif?.serial, profileMain,
                profileSub ?: profileMain, onvif?.ptzSupported == true,
                onvif?.uuid ?: input.uuid, input.mac, "RTSP stream verified with a known camera profile"
            )
        }

        if (onvif?.mainStreamUrl != null) {
            val main = withRtspCredentials(onvif.mainStreamUrl, input.username, input.password)
            val sub = onvif.subStreamUrl?.let { withRtspCredentials(it, input.username, input.password) }
            if (isPlayable(main, input)) {
                val verifiedSub = sub?.takeIf { isPlayable(it, input) }
                return Result(
                    true, "ONVIF_STREAM", onvif.manufacturer ?: input.brandHint ?: "ONVIF", onvif.model,
                    onvif.firmware, onvif.serial, main, verifiedSub ?: main, onvif.ptzSupported,
                    onvif.uuid ?: input.uuid, input.mac,
                    if (verifiedSub != null) "ONVIF main and sub streams verified" else "ONVIF main stream verified; sub stream falls back to main"
                )
            }
        }

        Log.w(TAG, "Configuration failed ip=${input.ip}; no authenticated playable RTSP stream")
        return Result(
            false, "NONE", onvif?.manufacturer ?: input.brandHint ?: "Unknown", onvif?.model,
            onvif?.firmware, onvif?.serial, null, null, onvif?.ptzSupported == true,
            onvif?.uuid ?: input.uuid, input.mac,
            if (onvif == null) "ONVIF authentication failed and no known RTSP profile played" else "ONVIF connected but its RTSP stream could not be verified"
        )
    }

    private suspend fun isPlayable(url: String, input: Input): Boolean {
        val result = RtspEndpointVerifier.verify(url, input.username, input.password)
        Log.d(TAG, "Stream tested endpoint=${RtspEndpointVerifier.safeEndpoint(url)} playable=${result.playable} status=${result.statusCode}")
        return result.playable || LibVlcStreamVerifier.verify(context, url, input.username, input.password)
    }

    private fun withRtspCredentials(url: String, user: String, pass: String): String {
        val uri = java.net.URI(url)
        fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        val port = if (uri.port > 0) uri.port else 554
        val path = uri.rawPath.orEmpty().ifBlank { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "rtsp://${encode(user)}:${encode(pass)}@${uri.host}:$port$path$query"
    }

    companion object { private const val TAG = "ALADIN_CAMERA_SETUP" }
}
