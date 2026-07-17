package com.aladin.aladincamviewer

import kotlinx.serialization.Serializable

@Serializable
data class ConfigModel(
    val cameras: List<CameraEntity>,
    val appPin: String,
    val offlineAlarm: Boolean
)
