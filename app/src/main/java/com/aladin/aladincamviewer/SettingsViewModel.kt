package com.aladin.aladincamviewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CameraRepository
    private val prefHelper: PreferenceHelper
    val allCameras: Flow<List<CameraEntity>>
    val recorderChannels: Flow<List<RecorderChannelWithRecorder>>

    init {
        val cameraDao = AppDatabase.getDatabase(application).cameraDao()
        repository = CameraRepository(application, cameraDao)
        prefHelper = PreferenceHelper(application)
        allCameras = repository.allCameras
        recorderChannels = RecorderRepository(application).enabledChannels
    }

    fun updatePin(pin: String) {
        prefHelper.setPin(pin)
    }

    fun hasPin() = prefHelper.hasPin

    fun updateOfflineAlarm(enabled: Boolean) {
        prefHelper.isOfflineAlarmEnabled = enabled
    }

    fun isOfflineAlarmEnabled() = prefHelper.isOfflineAlarmEnabled

    fun saveCamera(camera: CameraEntity) {
        viewModelScope.launch {
            if (camera.id == 0) repository.insert(camera) else repository.update(camera)
        }
    }

    fun exportConfig(outputStream: OutputStream, cameras: List<CameraEntity>, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val sanitized = cameras.map { camera -> camera.copy(
                    username = "", password = "", onvifUsername = "", onvifPassword = "",
                    mainStreamUrl = stripUserInfo(camera.mainStreamUrl),
                    subStreamUrl = stripUserInfo(camera.subStreamUrl)
                ) }
                val config = ConfigModel(sanitized, offlineAlarm = prefHelper.isOfflineAlarmEnabled)
                val jsonString = Json { prettyPrint = true }.encodeToString(ConfigModel.serializer(), config)
                outputStream.use { it.write(jsonString.toByteArray()) }
            }
            result.exceptionOrNull()?.let { AppLog.e("ALADIN_CONFIG", "Configuration export failed", it) }
            onComplete(result)
        }
    }

    fun importConfig(inputStream: InputStream, onComplete: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                require(jsonString.length <= 2_000_000) { "Configuration file is too large" }
                val config = Json { ignoreUnknownKeys = true }.decodeFromString<ConfigModel>(jsonString)
                val errors = ConfigValidator.validate(config)
                require(errors.isEmpty()) { errors.joinToString("; ") }
                val normalized = config.cameras.map { it.copy(ipAddress = it.ipAddress.trim()) }
                repository.replaceAll(normalized)
                prefHelper.isOfflineAlarmEnabled = config.offlineAlarm
                normalized.size
            }
            result.exceptionOrNull()?.let { AppLog.e("ALADIN_CONFIG", "Configuration import failed", it) }
            onComplete(result)
        }
    }

    private fun stripUserInfo(value: String): String = if (value.isBlank()) value else runCatching {
        val uri = URI(value)
        URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, uri.fragment).toASCIIString()
    }.getOrDefault("")
}
