package com.aladin.aladincamviewer

object CameraIdentityMatcher {
    fun strongMatch(camera: CameraEntity, device: DiscoveryDevice): Boolean {
        val cameraUuid = normalizeUuid(camera.uuid)
        val deviceUuid = normalizeUuid(device.uuid)
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

    private fun normalizeUuid(value: String?): String? = value
        ?.trim()
        ?.lowercase()
        ?.removePrefix("urn:")
        ?.removePrefix("uuid:")
        ?.takeIf { it.isNotBlank() }

    private fun normalizeMac(value: String?): String? = value
        ?.filter { it.isLetterOrDigit() }
        ?.lowercase()
        ?.takeIf { it.length == 12 }
}
