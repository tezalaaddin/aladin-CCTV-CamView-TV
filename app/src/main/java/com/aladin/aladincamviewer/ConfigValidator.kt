package com.aladin.aladincamviewer

import java.net.URI

object ConfigValidator {
    fun validate(config: ConfigModel): List<String> {
        val errors = mutableListOf<String>()
        if (config.cameras.size > 64) errors += "Too many cameras"
        val ips = mutableSetOf<String>()
        config.cameras.forEachIndexed { index, camera ->
            val ip = camera.ipAddress.trim()
            if (ip.isBlank()) errors += "Camera ${index + 1}: empty IP"
            if (!ips.add(ip)) errors += "Camera ${index + 1}: duplicate IP"
            if (camera.displayOrder !in 1..64) errors += "Camera ${index + 1}: invalid display order"
            if (!isRtsp(camera.mainStreamUrl)) errors += "Camera ${index + 1}: invalid main RTSP URL"
            if (camera.subStreamUrl.isNotBlank() && !isRtsp(camera.subStreamUrl)) {
                errors += "Camera ${index + 1}: invalid sub RTSP URL"
            }
        }
        return errors
    }

    private fun isRtsp(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("rtsp", true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
