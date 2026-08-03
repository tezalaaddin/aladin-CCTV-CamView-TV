package com.aladin.aladincamviewer

data class DiscoveryDevice(
    val ip: String,
    var mac: String? = null,
    var brand: String = "Generic",
    var model: String? = null,
    var firmware: String? = null,
    var serial: String? = null,
    var uuid: String? = null,
    val protocols: MutableSet<String> = mutableSetOf(),
    var isAdded: Boolean = false,
    var snapshotUri: String? = null,
    var isSelected: Boolean = false
) {
    fun isRecorderCandidate(): Boolean {
        val identity = "${model.orEmpty()} $brand".uppercase()
        return identity.contains("NVR") || identity.contains("DVR") ||
            Regex("DS-7[167]\\d{2}NI").containsMatchIn(identity)
    }
}
