package com.aladin.aladincamviewer

import java.util.UUID

object CameraIdentityMatcher {
    fun strongMatch(camera: CameraEntity, device: DiscoveryDevice): Boolean {
        val cameraUuid = canonicalUuid(camera.uuid)
        val deviceUuid = canonicalUuid(device.uuid)
        val cameraMac = normalizeMac(camera.macAddress)
        val deviceMac = normalizeMac(device.mac)
        return (cameraUuid != null && cameraUuid == deviceUuid) ||
            (cameraMac != null && cameraMac == deviceMac)
    }

    fun isBrandCompatible(cameraBrand: String, deviceBrand: String): Boolean {
        val camera = cameraBrand.trim().lowercase()
        val device = deviceBrand.trim().lowercase()
        if (camera in setOf("", "custom", "generic") || device in setOf("", "custom", "generic")) {
            return true
        }
        return camera == device || camera.contains(device) || device.contains(camera)
    }

    fun isValidUuid(value: String?): Boolean = canonicalUuid(value) != null

    private fun canonicalUuid(value: String?): String? {
        val candidate = value
            ?.trim()
            ?.lowercase()
            ?.removePrefix("urn:")
            ?.removePrefix("uuid:")
            ?: return null
        return runCatching { UUID.fromString(candidate).toString() }.getOrNull()
    }

    private fun normalizeMac(value: String?): String? = value
        ?.filter { it.isLetterOrDigit() }
        ?.lowercase()
        ?.takeIf { it.length == 12 }
}
