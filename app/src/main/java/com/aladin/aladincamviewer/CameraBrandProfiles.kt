package com.aladin.aladincamviewer

import java.net.URLEncoder

object CameraBrandProfiles {
    data class Pairing(val brand: String, val mainPath: String, val subPath: String)

    private val profiles = listOf(
        Pairing("Hikvision", "/Streaming/Channels/101", "/Streaming/Channels/102"),
        Pairing("Dahua", "/cam/realmonitor?channel=1&subtype=0", "/cam/realmonitor?channel=1&subtype=1"),
        Pairing("Tiandy", "/live/ch1", "/live/ch0"),
        Pairing("Tiandy", "/1/1", "/1/2"),
        Pairing("Tiandy", "/live/ch00_0", "/live/ch00_1"),
        Pairing("Uniview", "/unicast/c1/s0/live", "/unicast/c1/s1/live"),
        Pairing("Reolink", "/h264Preview_01_main", "/h264Preview_01_sub"),
        Pairing("Axis", "/axis-media/media.amp", "/axis-media/media.amp?streamprofile=Mobile"),
        Pairing("Hanwha", "/profile1/media.smp", "/profile2/media.smp"),
        Pairing("Vivotek", "/live.sdp", "/live2.sdp"),
        Pairing("Foscam", "/videoMain", "/videoSub"),
        Pairing("Tapo", "/stream1", "/stream2"),
        Pairing("AJCloud", "/live/ch0", "/live/ch1"),
        Pairing("XMeye", "/user=admin_password=_channel=1_stream=0.sdp", "/user=admin_password=_channel=1_stream=1.sdp")
    )

    fun candidates(brandHint: String?, ip: String, user: String, pass: String): List<Pair<String, String>> {
        val normalized = brandHint.orEmpty().lowercase()
        val preferred = profiles.filter { normalized.isNotBlank() && normalized != "generic" && it.brand.lowercase() in normalized }
        val commonFallbacks = profiles.filter { it.brand in setOf("Hikvision", "Dahua", "Tiandy", "Uniview", "Reolink", "Tapo", "AJCloud") }
        return (preferred + commonFallbacks).distinct().map { pairing ->
            buildUrl(ip, user, pass, pairing.mainPath) to buildUrl(ip, user, pass, pairing.subPath)
        }
    }

    fun knownBrands(): List<String> = profiles.map { it.brand }.distinct() + listOf("ONVIF", "Custom")

    private fun buildUrl(ip: String, user: String, pass: String, path: String): String {
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        val authority = if (ip.contains(':')) ip else "$ip:554"
        return "rtsp://${encode(user)}:${encode(pass)}@$authority$path"
    }
}
